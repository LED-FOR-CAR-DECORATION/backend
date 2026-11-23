package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.ManagedFirmwareController;
import com.lumintra.backend.device.api.request.UploadFirmwareRequest;
import com.lumintra.backend.device.api.response.UploadFirmwareResponse;
import com.lumintra.backend.device.api.request.FirmwareFilterRequest;
import com.lumintra.backend.device.api.response.FirmwareReleaseResponse;
import com.lumintra.backend.device.api.request.DeprecateFirmwareRequest;
import com.lumintra.backend.device.api.response.DeprecateFirmwareResponse;
import com.lumintra.backend.device.application.DeprecateFirmwareUseCase;
import com.lumintra.backend.device.application.ListFirmwareReleasesUseCase;
import com.lumintra.backend.shared.exception.device.FirmwareAlreadyDeprecatedException;
import com.lumintra.backend.shared.exception.device.FirmwareNotFoundException;
import org.springframework.http.MediaType;
import com.lumintra.backend.device.application.UploadFirmwareUseCase;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.device.FirmwareVersionConflictException;
import com.lumintra.backend.shared.exception.device.InvalidFirmwareBinaryException;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ManagedFirmwareController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class ManagedFirmwareControllerTest {

    private static final String PATH = "/api/management/admin/firmware";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadFirmwareUseCase uploadFirmwareUseCase;

    @MockitoBean
    private ListFirmwareReleasesUseCase listFirmwareReleasesUseCase;

    @MockitoBean
    private DeprecateFirmwareUseCase deprecateFirmwareUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private static MockMultipartHttpServletRequestBuilder validUpload() {
        byte[] binary = new byte[512];
        binary[0] = (byte) 0xE9;
        MockMultipartHttpServletRequestBuilder builder = multipart(PATH);
        builder.file(new MockMultipartFile("file", "app.bin", "application/octet-stream", binary));
        builder.param("modelId", "3").param("version", "1.2.0").param("changelog", "Fix BLE reconnect");
        return builder;
    }

    private static UploadFirmwareResponse sampleResponse() {
        return new UploadFirmwareResponse(12L, 3L, "Matrix 64x64", "1.2.0", FirmwareStatus.ACTIVE,
                "Fix BLE reconnect", 512L, "a".repeat(64), "Nguyen Van A", Instant.parse("2026-11-21T08:00:00Z"),
                null, null, null, 0L, 11L);
    }

    @Test
    @DisplayName("DEV-12 Controller UTCID01 [N]: ADMIN uploads multipart firmware and receives 201 with release fields")
    void upload_admin_returns201() throws Exception {
        when(uploadFirmwareUseCase.execute(eq("7"), any(UploadFirmwareRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(validUpload().with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.firmwareId").value(12))
                .andExpect(jsonPath("$.data.modelName").value("Matrix 64x64"))
                .andExpect(jsonPath("$.data.version").value("1.2.0"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.data.uploadedBy").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data.deprecatedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.installedDeviceCount").value(0))
                .andExpect(jsonPath("$.data.supersededFirmwareId").value(11));

        ArgumentCaptor<UploadFirmwareRequest> captor = ArgumentCaptor.forClass(UploadFirmwareRequest.class);
        verify(uploadFirmwareUseCase).execute(eq("7"), captor.capture());
        assertThat(captor.getValue().modelId()).isEqualTo(3L);
        assertThat(captor.getValue().version()).isEqualTo("1.2.0");
        assertThat(captor.getValue().file().getSize()).isEqualTo(512L);
    }

    @Test
    @DisplayName("DEV-12 Controller UTCID02 [A]: Non-ADMIN roles get 403 and anonymous callers get 401")
    void upload_nonAdminOrAnonymous_isRejected() throws Exception {
        for (String role : new String[]{"MANAGER", "STAFF", "DESIGNER", "CUSTOMER"}) {
            mockMvc.perform(validUpload().with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(validUpload()).andExpect(status().isUnauthorized());
        verifyNoInteractions(uploadFirmwareUseCase);
    }

    @Test
    @DisplayName("DEV-12 Controller UTCID03 [A]: Missing file, bad version or blank changelog returns 400 VALIDATION_FAILED")
    void upload_invalidFields_returns400() throws Exception {
        mockMvc.perform(multipart(PATH).param("modelId", "3").param("version", "1.2.0").param("changelog", "x")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(validUpload().param("version", "v1.2")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        for (String version : new String[]{"1.2", "01.2.0", "1.2.0-beta", "1234567.0.0"}) {
            MockMultipartHttpServletRequestBuilder request = multipart(PATH);
            request.file(new MockMultipartFile("file", "app.bin", "application/octet-stream", new byte[]{(byte) 0xE9}));
            request.param("modelId", "3").param("version", version).param("changelog", "notes");
            mockMvc.perform(request.with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        MockMultipartHttpServletRequestBuilder blankChangelog = multipart(PATH);
        blankChangelog.file(new MockMultipartFile("file", "app.bin", "application/octet-stream", new byte[]{(byte) 0xE9}));
        blankChangelog.param("modelId", "0").param("version", "1.0.0").param("changelog", "  ");
        mockMvc.perform(blankChangelog.with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(uploadFirmwareUseCase);
    }

    @Test
    @DisplayName("DEV-12 Controller UTCID04 [A]: Use case errors map to INVALID_FIRMWARE_BINARY, LED_MODEL_NOT_FOUND, FIRMWARE_VERSION_CONFLICT")
    void upload_useCaseErrors_mapToContractCodes() throws Exception {
        when(uploadFirmwareUseCase.execute(eq("7"), any(UploadFirmwareRequest.class)))
                .thenThrow(new InvalidFirmwareBinaryException("Firmware file is not an ESP32 image (missing 0xE9 magic byte)."))
                .thenThrow(new LedModelNotFoundException(3L))
                .thenThrow(new FirmwareVersionConflictException("Version 1.2.0 must be new and higher than 1.2.0 for LED model 3."));

        mockMvc.perform(validUpload().with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FIRMWARE_BINARY"));
        mockMvc.perform(validUpload().with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LED_MODEL_NOT_FOUND"));
        mockMvc.perform(validUpload().with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIRMWARE_VERSION_CONFLICT"));
    }

    private static ListFirmwareReleasesUseCase.Result sampleList() {
        FirmwareReleaseResponse release = FirmwareReleaseResponse.builder()
                .firmwareId(12L).modelId(3L).modelName("Matrix 64x64").version("1.2.0").status(FirmwareStatus.ACTIVE)
                .changelog("Fix BLE").fileSizeBytes(2048L).sha256("e".repeat(64)).uploadedBy("Nguyen Van A")
                .uploadedAt(Instant.parse("2026-11-20T10:00:00Z")).installedDeviceCount(5L).build();
        PaginationMeta meta = PaginationMeta.builder().page(0).size(1).totalElements(1).totalPages(1)
                .hasNext(false).hasPrevious(false).build();
        return new ListFirmwareReleasesUseCase.Result(List.of(release), meta);
    }

    @Test
    @DisplayName("DEV-11 Controller UTCID01 [N]: ADMIN lists releases with filters; items in data and paging in meta")
    void list_admin_returnsDataAndMeta() throws Exception {
        when(listFirmwareReleasesUseCase.execute(any(FirmwareFilterRequest.class))).thenReturn(sampleList());

        mockMvc.perform(get(PATH).param("status", "ACTIVE").param("size", "1").param("modelId", "3")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].firmwareId").value(12))
                .andExpect(jsonPath("$.data[0].modelName").value("Matrix 64x64"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].uploadedBy").value("Nguyen Van A"))
                .andExpect(jsonPath("$.data[0].deprecatedBy").value(nullValue()))
                .andExpect(jsonPath("$.data[0].installedDeviceCount").value(5))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(1))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
        verify(listFirmwareReleasesUseCase).execute(new FirmwareFilterRequest(0, 1, 3L, FirmwareStatus.ACTIVE));
    }

    @Test
    @DisplayName("DEV-11 Controller UTCID02 [B]: Defaults page 0 size 20; size 100 is accepted")
    void list_defaultsAndMaxSize() throws Exception {
        when(listFirmwareReleasesUseCase.execute(any(FirmwareFilterRequest.class))).thenReturn(sampleList());

        mockMvc.perform(get(PATH).with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get(PATH).param("size", "100").with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isOk());
        verify(listFirmwareReleasesUseCase).execute(new FirmwareFilterRequest(0, 20, null, null));
        verify(listFirmwareReleasesUseCase).execute(new FirmwareFilterRequest(0, 100, null, null));
    }

    @Test
    @DisplayName("DEV-11 Controller UTCID03 [A]: Invalid size, page, modelId or status returns 400")
    void list_invalidParams_returns400() throws Exception {
        String[][] invalid = {{"size", "101"}, {"size", "0"}, {"page", "-1"}, {"modelId", "0"}, {"status", "BROKEN"}};
        for (String[] param : invalid) {
            mockMvc.perform(get(PATH).param(param[0], param[1])
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(listFirmwareReleasesUseCase);
    }

    @Test
    @DisplayName("DEV-11 Controller UTCID04 [A]: Non-ADMIN roles get 403 and anonymous callers get 401")
    void list_nonAdmin_isRejected() throws Exception {
        for (String role : new String[]{"MANAGER", "STAFF", "CUSTOMER"}) {
            mockMvc.perform(get(PATH).with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(listFirmwareReleasesUseCase);
    }

    private static FirmwareReleaseResponse releaseWith(long id, String version, FirmwareStatus status) {
        return FirmwareReleaseResponse.builder()
                .firmwareId(id).modelId(3L).modelName("Matrix 64x64").version(version).status(status)
                .changelog("notes").fileSizeBytes(2048L).sha256("a".repeat(64)).uploadedBy("Nguyen Van A")
                .uploadedAt(Instant.parse("2026-11-20T10:00:00Z")).installedDeviceCount(0L).build();
    }

    @Test
    @DisplayName("DEV-13 Controller UTCID01 [N]: ADMIN deprecates a release and receives deprecated, newActiveFirmware, affectedDeviceCount")
    void deprecate_admin_returns200() throws Exception {
        when(deprecateFirmwareUseCase.execute(eq("7"), eq(12L), any(DeprecateFirmwareRequest.class)))
                .thenReturn(new DeprecateFirmwareResponse(
                        releaseWith(12L, "1.2.0", FirmwareStatus.DEPRECATED),
                        releaseWith(11L, "1.1.0", FirmwareStatus.ACTIVE), 5L));

        mockMvc.perform(patch(PATH + "/12/deprecate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Boot loop\"}")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deprecated.firmwareId").value(12))
                .andExpect(jsonPath("$.data.deprecated.status").value("DEPRECATED"))
                .andExpect(jsonPath("$.data.newActiveFirmware.firmwareId").value(11))
                .andExpect(jsonPath("$.data.newActiveFirmware.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.affectedDeviceCount").value(5));
        verify(deprecateFirmwareUseCase).execute("7", 12L, new DeprecateFirmwareRequest("Boot loop"));
    }

    @Test
    @DisplayName("DEV-13 Controller UTCID02 [B]: newActiveFirmware is null when nothing is promoted")
    void deprecate_noPromotion_returnsNullNewActive() throws Exception {
        when(deprecateFirmwareUseCase.execute(eq("7"), eq(11L), any(DeprecateFirmwareRequest.class)))
                .thenReturn(new DeprecateFirmwareResponse(releaseWith(11L, "1.1.0", FirmwareStatus.DEPRECATED), null, 0L));

        mockMvc.perform(patch(PATH + "/11/deprecate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Old\"}")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newActiveFirmware").value(nullValue()))
                .andExpect(jsonPath("$.data.affectedDeviceCount").value(0));
    }

    @Test
    @DisplayName("DEV-13 Controller UTCID03 [A]: Missing, blank or over-long reason and non-positive id return 400")
    void deprecate_invalidInput_returns400() throws Exception {
        String[] bodies = {"{}", "{\"reason\":\"   \"}", "{\"reason\":\"" + "x".repeat(1001) + "\"}"};
        for (String body : bodies) {
            mockMvc.perform(patch(PATH + "/12/deprecate").contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(patch(PATH + "/0/deprecate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(deprecateFirmwareUseCase);
    }

    @Test
    @DisplayName("DEV-13 Controller UTCID04 [A]: Errors map to FIRMWARE_NOT_FOUND (404) and FIRMWARE_ALREADY_DEPRECATED (409)")
    void deprecate_useCaseErrors_mapToContractCodes() throws Exception {
        when(deprecateFirmwareUseCase.execute(eq("7"), any(Long.class), any(DeprecateFirmwareRequest.class)))
                .thenThrow(new FirmwareNotFoundException(99L))
                .thenThrow(new FirmwareAlreadyDeprecatedException(12L));

        mockMvc.perform(patch(PATH + "/99/deprecate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FIRMWARE_NOT_FOUND"));
        mockMvc.perform(patch(PATH + "/12/deprecate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIRMWARE_ALREADY_DEPRECATED"));
    }

    @Test
    @DisplayName("DEV-13 Controller UTCID05 [A]: Non-ADMIN roles get 403 and anonymous callers get 401")
    void deprecate_nonAdmin_isRejected() throws Exception {
        for (String role : new String[]{"MANAGER", "STAFF", "CUSTOMER"}) {
            mockMvc.perform(patch(PATH + "/12/deprecate").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"x\"}")
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(patch(PATH + "/12/deprecate").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(deprecateFirmwareUseCase);
    }
}
