package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.DeviceController;
import com.lumintra.backend.device.api.request.CreateOtaSessionRequest;
import com.lumintra.backend.device.api.response.OtaSessionResponse;
import com.lumintra.backend.device.application.CheckFirmwareUpdateUseCase;
import com.lumintra.backend.device.application.ClaimDeviceUseCase;
import com.lumintra.backend.device.application.CreateOtaSessionUseCase;
import com.lumintra.backend.device.application.GetCustomerDeviceDetailUseCase;
import com.lumintra.backend.device.application.ListCustomerDevicesUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.device.DeviceNotBoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.FirmwareNotActiveException;
import com.lumintra.backend.shared.exception.device.FirmwareNotFoundException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class DeviceOtaSessionControllerTest {

    private static final String PATH = "/api/devices/2001/ota/sessions";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final String TOKEN = "ab".repeat(32);

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CreateOtaSessionUseCase createOtaSessionUseCase;
    @MockitoBean private CheckFirmwareUpdateUseCase checkFirmwareUpdateUseCase;
    @MockitoBean private ClaimDeviceUseCase claimDeviceUseCase;
    @MockitoBean private ListCustomerDevicesUseCase listCustomerDevicesUseCase;
    @MockitoBean private GetCustomerDeviceDetailUseCase getCustomerDeviceDetailUseCase;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private Tracer tracer;

    private static String body(Object firmwareId, String nonce) {
        return "{\"firmwareId\":" + firmwareId + ",\"nonce\":" + (nonce == null ? "null" : "\"" + nonce + "\"") + "}";
    }

    @Test
    @DisplayName("DEV-21 Controller UTCID01 [N]: Customer receives 201 with sessionId, token, expiresAt and firmware")
    void create_customer_returns201() throws Exception {
        when(createOtaSessionUseCase.execute(eq(55L), eq(2001L), any(CreateOtaSessionRequest.class)))
                .thenReturn(OtaSessionResponse.builder()
                        .sessionId(31L).token(TOKEN).expiresAt(Instant.parse("2026-11-23T10:10:00Z"))
                        .firmware(OtaSessionResponse.SessionFirmware.builder()
                                .firmwareId(12L).version("1.2.0").fileSizeBytes(1024L).sha256("c".repeat(64)).build())
                        .build());

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(12, NONCE))
                        .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sessionId").value(31))
                .andExpect(jsonPath("$.data.token").value(TOKEN))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-11-23T10:10:00Z"))
                .andExpect(jsonPath("$.data.firmware.firmwareId").value(12))
                .andExpect(jsonPath("$.data.firmware.version").value("1.2.0"))
                .andExpect(jsonPath("$.data.firmware.fileSizeBytes").value(1024))
                .andExpect(jsonPath("$.data.firmware.sha256").value("c".repeat(64)));
        verify(createOtaSessionUseCase).execute(55L, 2001L, new CreateOtaSessionRequest(12L, NONCE));
    }

    @Test
    @DisplayName("DEV-21 Controller UTCID02 [A]: Missing firmwareId or a nonce that is not 32 lowercase hex returns 400")
    void create_invalidBody_returns400() throws Exception {
        String[] bodies = {
                body(null, NONCE), body(0, NONCE), body(12, null),
                body(12, NONCE.toUpperCase()), body(12, NONCE.substring(1)), body(12, NONCE + "0"), body(12, "g".repeat(32))
        };
        for (String content : bodies) {
            mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(content)
                            .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(createOtaSessionUseCase);
    }

    @Test
    @DisplayName("DEV-21 Controller UTCID03 [A]: Errors map to LED_INSTANCE_NOT_FOUND, DEVICE_OWNERSHIP_MISMATCH, DEVICE_NOT_BOUND, FIRMWARE_NOT_FOUND, FIRMWARE_NOT_ACTIVE")
    void create_useCaseErrors_mapToCodes() throws Exception {
        when(createOtaSessionUseCase.execute(eq(55L), anyLong(), any(CreateOtaSessionRequest.class)))
                .thenThrow(new LedInstanceNotFoundException("missing"))
                .thenThrow(new DeviceOwnershipMismatchException("not yours"))
                .thenThrow(new DeviceNotBoundException("returned"))
                .thenThrow(new FirmwareNotFoundException(404L))
                .thenThrow(new FirmwareNotActiveException("superseded"));

        Object[][] expected = {
                {404, "LED_INSTANCE_NOT_FOUND"}, {403, "DEVICE_OWNERSHIP_MISMATCH"}, {409, "DEVICE_NOT_BOUND"},
                {404, "FIRMWARE_NOT_FOUND"}, {409, "FIRMWARE_NOT_ACTIVE"}
        };
        for (Object[] row : expected) {
            mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(12, NONCE))
                            .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                    .andExpect(status().is((int) row[0]))
                    .andExpect(jsonPath("$.code").value((String) row[1]));
        }
    }

    @Test
    @DisplayName("DEV-21 Controller UTCID04 [A]: Internal roles get 403 and anonymous callers get 401")
    void create_nonCustomer_isRejected() throws Exception {
        for (String role : new String[]{"ADMIN", "MANAGER", "STAFF"}) {
            mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(12, NONCE))
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(12, NONCE)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(createOtaSessionUseCase);
    }
}
