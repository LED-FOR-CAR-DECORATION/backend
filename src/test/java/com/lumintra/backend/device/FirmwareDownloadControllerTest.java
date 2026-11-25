package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.FirmwareDownloadController;
import com.lumintra.backend.device.api.response.OtaManifestResponse;
import com.lumintra.backend.device.application.FetchOtaManifestUseCase;
import com.lumintra.backend.device.application.StartFirmwareDownloadUseCase;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.device.OtaSessionNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FirmwareDownloadController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class FirmwareDownloadControllerTest {

    private static final String TOKEN = "ab".repeat(32);
    private static final String PATH = "/api/firmware/downloads/" + TOKEN + "/manifest";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private FetchOtaManifestUseCase fetchOtaManifestUseCase;
    @MockitoBean private StartFirmwareDownloadUseCase startFirmwareDownloadUseCase;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private Tracer tracer;

    private static OtaManifestResponse manifest() {
        return OtaManifestResponse.builder().deviceId("AABBCC112201").nonce("0123456789abcdef0123456789abcdef")
                .version("1.2.0").sha256("c".repeat(64)).size(1_048_576L).build();
    }

    @Test
    @DisplayName("DEV-22 Controller UTCID01 [N]: Anonymous device gets 200 with data.deviceId/nonce/version/sha256/size")
    void manifest_anonymous_returns200() throws Exception {
        when(fetchOtaManifestUseCase.execute(TOKEN)).thenReturn(manifest());

        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.data.deviceId").value("AABBCC112201"))
                .andExpect(jsonPath("$.data.nonce").value("0123456789abcdef0123456789abcdef"))
                .andExpect(jsonPath("$.data.version").value("1.2.0"))
                .andExpect(jsonPath("$.data.sha256").value("c".repeat(64)))
                .andExpect(jsonPath("$.data.size").value(1_048_576))
                .andExpect(jsonPath("$.meta").doesNotExist());
        verify(fetchOtaManifestUseCase).execute(TOKEN);
    }

    @Test
    @DisplayName("DEV-22 Controller UTCID02 [N]: Response carries an exact Content-Length under 2 KB and is not cached")
    void manifest_hasContentLengthUnder2Kb() throws Exception {
        when(fetchOtaManifestUseCase.execute(TOKEN)).thenReturn(manifest());

        MvcResult result = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(result.getResponse().getHeader("Content-Length")).isEqualTo(String.valueOf(body.length));
        assertThat(body.length).isPositive().isLessThan(2048);
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        assertThat(new String(body, StandardCharsets.UTF_8)).startsWith("{\"data\":{");
    }

    @Test
    @DisplayName("DEV-22 Controller UTCID03 [A]: Unusable token returns 404 OTA_SESSION_NOT_FOUND")
    void manifest_unusableToken_returns404() throws Exception {
        when(fetchOtaManifestUseCase.execute(anyString())).thenThrow(new OtaSessionNotFoundException());

        mockMvc.perform(get(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OTA_SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("DEV-22 Controller UTCID04 [A]: Only GET on the manifest path is public")
    void manifest_onlyGetIsPublic() throws Exception {
        mockMvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/firmware/downloads/" + TOKEN + "/other")).andExpect(status().isUnauthorized());
    }

    private static final String BINARY_PATH = "/api/firmware/downloads/" + TOKEN + "/binary";
    private static final byte[] BINARY = {(byte) 0xE9, 0, 1, 2, 3, 4, 5, 6, 7, 8};

    @Test
    @DisplayName("DEV-23 Controller UTCID01 [N]: Anonymous device gets 200 octet-stream with Content-Length = file size")
    void binary_anonymous_streamsFile() throws Exception {
        when(startFirmwareDownloadUseCase.execute(TOKEN))
                .thenReturn(new StartFirmwareDownloadUseCase.FirmwareDownload(BINARY.length, new ByteArrayInputStream(BINARY)));

        MvcResult result = mockMvc.perform(get(BINARY_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().bytes(BINARY))
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Length")).isEqualTo(String.valueOf(BINARY.length));
        assertThat(result.getResponse().getHeader("Location")).isNull();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        verify(startFirmwareDownloadUseCase).execute(TOKEN);
    }

    @Test
    @DisplayName("DEV-23 Controller UTCID02 [A]: Unusable token or attempts used up returns 404 OTA_SESSION_NOT_FOUND")
    void binary_unusableToken_returns404() throws Exception {
        when(startFirmwareDownloadUseCase.execute(anyString())).thenThrow(new OtaSessionNotFoundException());

        mockMvc.perform(get(BINARY_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OTA_SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("DEV-23 Controller UTCID03 [A]: Missing or mismatched stored binary returns 500 STORAGE_SERVICE_ERROR, not a partial file")
    void binary_storageFailure_returns500() throws Exception {
        when(startFirmwareDownloadUseCase.execute(anyString())).thenThrow(new StorageServiceException("missing"));

        mockMvc.perform(get(BINARY_PATH))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("STORAGE_SERVICE_ERROR"));
    }

    @Test
    @DisplayName("DEV-23 Controller UTCID04 [A]: Only GET on the binary path is public")
    void binary_onlyGetIsPublic() throws Exception {
        mockMvc.perform(post(BINARY_PATH)).andExpect(status().isUnauthorized());
    }
}
