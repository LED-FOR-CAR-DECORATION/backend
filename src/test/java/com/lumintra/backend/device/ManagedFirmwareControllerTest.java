package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.ManagedFirmwareController;
import com.lumintra.backend.device.api.request.UploadFirmwareRequest;
import com.lumintra.backend.device.api.response.UploadFirmwareResponse;
import com.lumintra.backend.device.application.UploadFirmwareUseCase;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
}
