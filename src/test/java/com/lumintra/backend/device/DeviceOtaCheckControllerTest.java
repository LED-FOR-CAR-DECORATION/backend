package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.DeviceController;
import com.lumintra.backend.device.api.response.OtaCheckResponse;
import com.lumintra.backend.device.application.CheckFirmwareUpdateUseCase;
import com.lumintra.backend.device.application.ClaimDeviceUseCase;
import com.lumintra.backend.device.application.GetCustomerDeviceDetailUseCase;
import com.lumintra.backend.device.application.ListCustomerDevicesUseCase;
import com.lumintra.backend.device.domain.model.OtaVersionStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.device.DeviceNotBoundException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class DeviceOtaCheckControllerTest {

    private static final String PATH = "/api/devices/2001/ota/check";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CheckFirmwareUpdateUseCase checkFirmwareUpdateUseCase;
    @MockitoBean private com.lumintra.backend.device.application.CreateOtaSessionUseCase createOtaSessionUseCase;
    @MockitoBean private com.lumintra.backend.device.application.ReportInstalledFirmwareUseCase reportInstalledFirmwareUseCase;
    @MockitoBean private ClaimDeviceUseCase claimDeviceUseCase;
    @MockitoBean private ListCustomerDevicesUseCase listCustomerDevicesUseCase;
    @MockitoBean private GetCustomerDeviceDetailUseCase getCustomerDeviceDetailUseCase;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private Tracer tracer;

    @Test
    @DisplayName("DEV-10 Controller UTCID01 [N]: Customer receives update result with targetFirmware and isRollback")
    void check_customer_returnsResult() throws Exception {
        when(checkFirmwareUpdateUseCase.execute(55L, 2001L, "1.0.0")).thenReturn(OtaCheckResponse.builder()
                .deviceId(2001L).currentVersion("1.0.0").currentVersionStatus(OtaVersionStatus.SUPERSEDED)
                .updateAvailable(true).isRollback(false)
                .targetFirmware(OtaCheckResponse.TargetFirmware.builder()
                        .firmwareId(12L).version("1.2.0").changelog("Fix BLE").fileSizeBytes(1024L).sha256("a".repeat(64)).build())
                .build());

        mockMvc.perform(get(PATH).param("currentVersion", "1.0.0")
                        .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(2001))
                .andExpect(jsonPath("$.data.currentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.data.currentVersionStatus").value("SUPERSEDED"))
                .andExpect(jsonPath("$.data.updateAvailable").value(true))
                .andExpect(jsonPath("$.data.isRollback").value(false))
                .andExpect(jsonPath("$.data.rollback").doesNotExist())
                .andExpect(jsonPath("$.data.targetFirmware.firmwareId").value(12))
                .andExpect(jsonPath("$.data.targetFirmware.version").value("1.2.0"))
                .andExpect(jsonPath("$.data.targetFirmware.fileSizeBytes").value(1024))
                .andExpect(jsonPath("$.data.targetFirmware.sha256").value("a".repeat(64)));
    }

    @Test
    @DisplayName("DEV-10 Controller UTCID02 [B]: currentVersion is optional; no update gives null targetFirmware")
    void check_withoutVersion_passesNull() throws Exception {
        when(checkFirmwareUpdateUseCase.execute(eq(55L), eq(2001L), isNull())).thenReturn(OtaCheckResponse.builder()
                .deviceId(2001L).currentVersion(null).currentVersionStatus(OtaVersionStatus.UNKNOWN)
                .updateAvailable(false).isRollback(false).build());

        mockMvc.perform(get(PATH).with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.currentVersionStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.targetFirmware").value(nullValue()));
        verify(checkFirmwareUpdateUseCase).execute(55L, 2001L, null);
    }

    @Test
    @DisplayName("DEV-10 Controller UTCID03 [A]: Non-semver currentVersion or non-positive id returns 400")
    void check_invalidInput_returns400() throws Exception {
        for (String version : new String[]{"1.0", "v1.0.0", "01.0.0", "1.0.0-beta"}) {
            mockMvc.perform(get(PATH).param("currentVersion", version)
                            .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/devices/0/ota/check")
                        .with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(checkFirmwareUpdateUseCase);
    }

    @Test
    @DisplayName("DEV-10 Controller UTCID04 [A]: Errors map to LED_INSTANCE_NOT_FOUND, DEVICE_OWNERSHIP_MISMATCH and DEVICE_NOT_BOUND")
    void check_useCaseErrors_mapToCodes() throws Exception {
        when(checkFirmwareUpdateUseCase.execute(eq(55L), anyLong(), any()))
                .thenThrow(new LedInstanceNotFoundException("missing"))
                .thenThrow(new DeviceOwnershipMismatchException("not yours"))
                .thenThrow(new DeviceNotBoundException("returned"));

        mockMvc.perform(get(PATH).with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LED_INSTANCE_NOT_FOUND"));
        mockMvc.perform(get(PATH).with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_OWNERSHIP_MISMATCH"));
        mockMvc.perform(get(PATH).with(SecurityMockMvcRequestPostProcessors.user("55").roles("CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_BOUND"));
    }

    @Test
    @DisplayName("DEV-10 Controller UTCID05 [A]: Internal roles get 403 and anonymous callers get 401")
    void check_nonCustomer_isRejected() throws Exception {
        for (String role : new String[]{"ADMIN", "MANAGER", "STAFF"}) {
            mockMvc.perform(get(PATH).with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(checkFirmwareUpdateUseCase);
    }
}
