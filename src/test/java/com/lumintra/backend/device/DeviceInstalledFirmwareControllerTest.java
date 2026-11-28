package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.DeviceController;
import com.lumintra.backend.device.api.request.ReportInstalledFirmwareRequest;
import com.lumintra.backend.device.api.response.InstalledFirmwareResponse;
import com.lumintra.backend.device.application.CheckFirmwareUpdateUseCase;
import com.lumintra.backend.device.application.ClaimDeviceUseCase;
import com.lumintra.backend.device.application.CreateOtaSessionUseCase;
import com.lumintra.backend.device.application.GetCustomerDeviceDetailUseCase;
import com.lumintra.backend.device.application.ListCustomerDevicesUseCase;
import com.lumintra.backend.device.application.ReportInstalledFirmwareUseCase;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.device.DeviceNotBoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.FirmwareModelMismatchException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class DeviceInstalledFirmwareControllerTest {

    private static final String PATH = "/api/devices/2001/firmware";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ReportInstalledFirmwareUseCase reportInstalledFirmwareUseCase;
    @MockitoBean private CreateOtaSessionUseCase createOtaSessionUseCase;
    @MockitoBean private CheckFirmwareUpdateUseCase checkFirmwareUpdateUseCase;
    @MockitoBean private ClaimDeviceUseCase claimDeviceUseCase;
    @MockitoBean private ListCustomerDevicesUseCase listCustomerDevicesUseCase;
    @MockitoBean private GetCustomerDeviceDetailUseCase getCustomerDeviceDetailUseCase;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private Tracer tracer;

    private static String body(Object firmwareId) {
        return "{\"firmwareId\":" + firmwareId + "}";
    }

    @Test
    @DisplayName("DEV-10B Controller UTCID01 [N]: Customer receives 200 with deviceId, firmwareId, version, firmwareStatus, firmwareUpdatedAt")
    void report_customer_returns200() throws Exception {
        when(reportInstalledFirmwareUseCase.execute(eq(55L), eq(2001L), any(ReportInstalledFirmwareRequest.class)))
                .thenReturn(InstalledFirmwareResponse.builder().deviceId(2001L).firmwareId(12L).version("1.2.0")
                        .firmwareStatus(FirmwareStatus.ACTIVE).firmwareUpdatedAt(Instant.parse("2026-11-24T10:15:00Z"))
                        .build());

        mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content(body(12))
                        .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(2001))
                .andExpect(jsonPath("$.data.firmwareId").value(12))
                .andExpect(jsonPath("$.data.version").value("1.2.0"))
                .andExpect(jsonPath("$.data.firmwareStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.firmwareUpdatedAt").value("2026-11-24T10:15:00Z"));
        verify(reportInstalledFirmwareUseCase).execute(55L, 2001L, new ReportInstalledFirmwareRequest(12L));
    }

    @Test
    @DisplayName("DEV-10B Controller UTCID02 [A]: Missing, zero or negative firmwareId returns 400")
    void report_invalidBody_returns400() throws Exception {
        for (String content : new String[]{body(null), body(0), body(-1), "{}"}) {
            mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content(content)
                            .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(reportInstalledFirmwareUseCase);
    }

    @Test
    @DisplayName("DEV-10B Controller UTCID03 [A]: Errors map to LED_INSTANCE_NOT_FOUND, DEVICE_OWNERSHIP_MISMATCH, DEVICE_NOT_BOUND, FIRMWARE_NOT_FOUND, FIRMWARE_MODEL_MISMATCH")
    void report_useCaseErrors_mapToCodes() throws Exception {
        when(reportInstalledFirmwareUseCase.execute(eq(55L), anyLong(), any(ReportInstalledFirmwareRequest.class)))
                .thenThrow(new LedInstanceNotFoundException("missing"))
                .thenThrow(new DeviceOwnershipMismatchException("not yours"))
                .thenThrow(new DeviceNotBoundException("returned"))
                .thenThrow(new FirmwareNotFoundException(404L))
                .thenThrow(new FirmwareModelMismatchException("other model"));

        Object[][] expected = {
                {404, "LED_INSTANCE_NOT_FOUND"}, {403, "DEVICE_OWNERSHIP_MISMATCH"}, {409, "DEVICE_NOT_BOUND"},
                {404, "FIRMWARE_NOT_FOUND"}, {409, "FIRMWARE_MODEL_MISMATCH"}
        };
        for (Object[] row : expected) {
            mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content(body(12))
                            .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                    .andExpect(status().is((int) row[0]))
                    .andExpect(jsonPath("$.code").value((String) row[1]));
        }
    }

    @Test
    @DisplayName("DEV-10B Controller UTCID04 [A]: Internal roles get 403 and anonymous callers get 401")
    void report_nonCustomer_isRejected() throws Exception {
        for (String role : new String[]{"ADMIN", "MANAGER", "STAFF"}) {
            mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content(body(12))
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(patch(PATH).contentType(MediaType.APPLICATION_JSON).content(body(12)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reportInstalledFirmwareUseCase);
    }
}
