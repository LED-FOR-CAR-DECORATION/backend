package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.DeviceController;
import com.lumintra.backend.device.api.request.ClaimDeviceRequest;
import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.application.ClaimDeviceUseCase;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.device.DeviceAlreadyBoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.InvalidDeviceQrException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.exception.device.OrderNotDeliveredYetException;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClaimDeviceUseCase claimDeviceUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private static final String VALID_REQUEST_JSON = """
            {
              "qrPayload": "{\\"v\\":1,\\"sn\\":\\"LMT-64X64-0005\\",\\"mac\\":\\"AA:BB:CC:11:22:05\\",\\"sig\\":\\"9f2c8b01a4d7e6f5c3b2a1908f7e6d5c4b3a291807f6e5d4c3b2a1908f7e6d5c\\"}",
              "deviceName": "Rear Window LED"
            }
            """;

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-93 CTRL-01 [N]: CUSTOMER claims device via QR and receives 200 OK with warranty and hardware spec")
    void claimDevice_asCustomer_returns200() throws Exception {
        ClaimDeviceResponse response = ClaimDeviceResponse.builder()
                .deviceId(1005L)
                .serialNumber("LMT-64X64-0005")
                .macAddress("AA:BB:CC:11:22:05")
                .deviceName("Rear Window LED")
                .modelId(101L)
                .modelName("Lumintra Matrix 64x64 Pro")
                .spec(LedModelListResponse.LedSpec.builder()
                        .widthPx(64)
                        .heightPx(64)
                        .attributes(List.of(new LedModelListResponse.SpecAttribute("Voltage", "5V USB-C")))
                        .build())
                .boundAt(Instant.parse("2026-11-14T08:00:00Z"))
                .warrantyExpiresAt(Instant.parse("2027-05-13T08:00:00Z"))
                .build();

        when(claimDeviceUseCase.execute(eq(42L), any(ClaimDeviceRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(1005))
                .andExpect(jsonPath("$.serialNumber").value("LMT-64X64-0005"))
                .andExpect(jsonPath("$.macAddress").value("AA:BB:CC:11:22:05"))
                .andExpect(jsonPath("$.deviceName").value("Rear Window LED"))
                .andExpect(jsonPath("$.modelId").value(101))
                .andExpect(jsonPath("$.modelName").value("Lumintra Matrix 64x64 Pro"))
                .andExpect(jsonPath("$.spec.widthPx").value(64))
                .andExpect(jsonPath("$.spec.heightPx").value(64))
                .andExpect(jsonPath("$.spec.attributes[0].label").value("Voltage"))
                .andExpect(jsonPath("$.boundAt").value("2026-11-14T08:00:00Z"))
                .andExpect(jsonPath("$.warrantyExpiresAt").value("2027-05-13T08:00:00Z"));
    }

    @Test
    @DisplayName("DEV-93 CTRL-02 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void claimDevice_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(claimDeviceUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "invalid-id", roles = {"CUSTOMER"})
    @DisplayName("DEV-93 CTRL-03 [A]: Non-numeric customer principal in token returns 401 INVALID_CREDENTIALS")
    void claimDevice_invalidPrincipal_returns401() throws Exception {
        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verify(claimDeviceUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "1", roles = {"MANAGER"})
    @DisplayName("DEV-93 CTRL-04 [A]: Non-CUSTOMER role (MANAGER) returns 403 FORBIDDEN")
    void claimDevice_asManager_returns403() throws Exception {
        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(claimDeviceUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-93 CTRL-05 [B]: Blank qrPayload or oversized deviceName returns 400 VALIDATION_FAILED")
    void claimDevice_invalidRequestBody_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrPayload\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String oversizedName = "A".repeat(256);
        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrPayload\":\"{\\\"v\\\":1}\",\"deviceName\":\"" + oversizedName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(claimDeviceUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-93 CTRL-06 [A]: InvalidDeviceQrException maps to 400 INVALID_DEVICE_QR")
    void claimDevice_invalidQrSignature_returns400InvalidDeviceQr() throws Exception {
        when(claimDeviceUseCase.execute(eq(42L), any()))
                .thenThrow(new InvalidDeviceQrException("QR code signature verification failed."));

        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DEVICE_QR"));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-93 CTRL-07 [A]: LedInstanceNotFoundException maps to 404 LED_INSTANCE_NOT_FOUND")
    void claimDevice_instanceNotFound_returns404() throws Exception {
        when(claimDeviceUseCase.execute(eq(42L), any()))
                .thenThrow(new LedInstanceNotFoundException("LED instance not found."));

        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LED_INSTANCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-93 CTRL-08 [A]: DeviceOwnershipMismatchException maps to 403 DEVICE_OWNERSHIP_MISMATCH")
    void claimDevice_ownershipMismatch_returns403DeviceOwnershipMismatch() throws Exception {
        when(claimDeviceUseCase.execute(eq(42L), any()))
                .thenThrow(new DeviceOwnershipMismatchException("Not owned by customer."));

        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_OWNERSHIP_MISMATCH"));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-93 CTRL-09 [A]: DeviceAlreadyBoundException and OrderNotDeliveredYetException map to 409 CONFLICT")
    void claimDevice_alreadyBoundOrOrderNotDelivered_returns409() throws Exception {
        when(claimDeviceUseCase.execute(eq(42L), any()))
                .thenThrow(new DeviceAlreadyBoundException("Device is already bound."));

        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_ALREADY_BOUND"));

        when(claimDeviceUseCase.execute(eq(42L), any()))
                .thenThrow(new OrderNotDeliveredYetException("Order must be DELIVERED or COMPLETED."));

        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_DELIVERED_YET"));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-93 CTRL-10 [A]: Unexpected runtime exception maps to 500 INTERNAL_SERVER_ERROR")
    void claimDevice_unexpectedFailure_returns500() throws Exception {
        when(claimDeviceUseCase.execute(eq(42L), any()))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(post("/api/devices/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
