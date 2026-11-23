package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.DeviceController;
import com.lumintra.backend.device.api.request.ClaimDeviceRequest;
import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.api.response.CustomerDeviceDetailResponse;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.application.ClaimDeviceUseCase;
import com.lumintra.backend.device.application.GetCustomerDeviceDetailUseCase;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private com.lumintra.backend.device.application.ListCustomerDevicesUseCase listCustomerDevicesUseCase;

    @MockitoBean
    private GetCustomerDeviceDetailUseCase getCustomerDeviceDetailUseCase;

    @MockitoBean
    private com.lumintra.backend.device.application.CheckFirmwareUpdateUseCase checkFirmwareUpdateUseCase;

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

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-94 CTRL-11 [N]: GET /api/devices returns 200 OK wrapped in ApiResponse(data, meta) with default pagination")
    void listDevices_authenticatedCustomer_returns200WithApiResponseEnvelopeAndDefaultPagination() throws Exception {
        var item = com.lumintra.backend.device.api.response.CustomerDeviceListResponse.CustomerDeviceItem.builder()
                .deviceId(2002L)
                .serialNumber("SN-2026-0002")
                .macAddress("AA:BB:CC:DD:EE:02")
                .deviceName("Rear Window Matrix")
                .status(com.lumintra.backend.device.domain.model.LedInstanceStatus.BOUND)
                .modelId(101L)
                .modelName("Lumintra Neo 64x16")
                .widthPx(64)
                .heightPx(16)
                .currentFirmwareId(3L)
                .boundAt(Instant.parse("2026-11-14T10:15:30Z"))
                .warrantyExpiresAt(Instant.parse("2027-11-14T10:15:30Z"))
                .warrantyStatus(com.lumintra.backend.device.domain.model.WarrantyStatus.ACTIVE)
                .build();

        var meta = com.lumintra.backend.shared.response.PaginationMeta.builder()
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(listCustomerDevicesUseCase.execute(
                42L,
                new com.lumintra.backend.device.api.request.CustomerDeviceFilterRequest(0, 20)))
                .thenReturn(com.lumintra.backend.device.api.response.CustomerDeviceListResponse.builder()
                        .items(List.of(item))
                        .meta(meta)
                        .build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deviceId").value(2002))
                .andExpect(jsonPath("$.data[0].serialNumber").value("SN-2026-0002"))
                .andExpect(jsonPath("$.data[0].macAddress").value("AA:BB:CC:DD:EE:02"))
                .andExpect(jsonPath("$.data[0].deviceName").value("Rear Window Matrix"))
                .andExpect(jsonPath("$.data[0].status").value("BOUND"))
                .andExpect(jsonPath("$.data[0].modelId").value(101))
                .andExpect(jsonPath("$.data[0].modelName").value("Lumintra Neo 64x16"))
                .andExpect(jsonPath("$.data[0].widthPx").value(64))
                .andExpect(jsonPath("$.data[0].heightPx").value(16))
                .andExpect(jsonPath("$.data[0].currentFirmwareId").value(3))
                .andExpect(jsonPath("$.data[0].boundAt").value("2026-11-14T10:15:30Z"))
                .andExpect(jsonPath("$.data[0].warrantyExpiresAt").value("2027-11-14T10:15:30Z"))
                .andExpect(jsonPath("$.data[0].warrantyStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.meta.hasPrevious").value(false));

        verify(listCustomerDevicesUseCase).execute(
                42L,
                new com.lumintra.backend.device.api.request.CustomerDeviceFilterRequest(0, 20));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-94 CTRL-12 [B]: GET /api/devices binds custom and boundary pagination params and returns empty data with meta")
    void listDevices_customAndEmptyPagination_returns200WithMeta() throws Exception {
        var meta = com.lumintra.backend.shared.response.PaginationMeta.builder()
                .page(2)
                .size(1)
                .totalElements(0)
                .totalPages(0)
                .hasNext(false)
                .hasPrevious(true)
                .build();

        when(listCustomerDevicesUseCase.execute(eq(42L), any()))
                .thenReturn(com.lumintra.backend.device.api.response.CustomerDeviceListResponse.builder()
                        .items(List.of())
                        .meta(meta)
                        .build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices")
                        .param("page", "2")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.size").value(1))
                .andExpect(jsonPath("$.meta.totalElements").value(0))
                .andExpect(jsonPath("$.meta.totalPages").value(0))
                .andExpect(jsonPath("$.meta.hasNext").value(false))
                .andExpect(jsonPath("$.meta.hasPrevious").value(true));

        verify(listCustomerDevicesUseCase).execute(
                42L,
                new com.lumintra.backend.device.api.request.CustomerDeviceFilterRequest(2, 1));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices")
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk());

        verify(listCustomerDevicesUseCase).execute(
                42L,
                new com.lumintra.backend.device.api.request.CustomerDeviceFilterRequest(0, 100));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-94 CTRL-13 [A]: GET /api/devices returns 400 VALIDATION_FAILED on invalid pagination query parameters")
    void listDevices_invalidPaginationParams_returns400ValidationFailed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(listCustomerDevicesUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("DEV-94 CTRL-14 [A]: GET /api/devices returns 401 UNAUTHORIZED when unauthenticated")
    void listDevices_unauthenticated_returns401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(listCustomerDevicesUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "42", roles = {"STAFF"})
    @DisplayName("DEV-94 CTRL-15 [A]: GET /api/devices returns 403 FORBIDDEN when authenticated user is not CUSTOMER")
    void listDevices_nonCustomerRole_returns403() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(listCustomerDevicesUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-94 CTRL-16 [A]: GET /api/devices maps unexpected runtime exception to 500 INTERNAL_SERVER_ERROR")
    void listDevices_unexpectedFailure_returns500() throws Exception {
        when(listCustomerDevicesUseCase.execute(eq(42L), any()))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/devices"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-95 CTRL-17 [N]: GET /api/devices/{id} returns 200 OK wrapped in ApiResponse with model spec and fulfillment")
    void getDeviceDetail_asOwnedCustomer_returns200WithApiResponseData() throws Exception {
        CustomerDeviceDetailResponse response = CustomerDeviceDetailResponse.builder()
                .deviceId(2001L)
                .serialNumber("LMT-64X64-SEED-0001")
                .macAddress("AA:BB:CC:11:22:01")
                .deviceName("Rear Window Matrix")
                .status(LedInstanceStatus.BOUND)
                .currentFirmwareId(12L)
                .boundAt(Instant.parse("2026-11-09T06:00:00Z"))
                .warrantyExpiresAt(Instant.parse("2027-05-08T06:00:00Z"))
                .warrantyStatus(WarrantyStatus.ACTIVE)
                .model(CustomerDeviceDetailResponse.ModelInfo.builder()
                        .modelId(101L)
                        .productId(301L)
                        .modelName("Lumintra Matrix 64x64 Pro")
                        .status(LedModelStatus.ACTIVE)
                        .spec(LedModelListResponse.LedSpec.builder()
                                .widthPx(64)
                                .heightPx(64)
                                .attributes(List.of(new LedModelListResponse.SpecAttribute("Voltage", "5V USB-C")))
                                .build())
                        .build())
                .fulfillment(CustomerDeviceDetailResponse.FulfillmentInfo.builder()
                        .orderId(55L)
                        .orderItemId(102L)
                        .build())
                .build();

        when(getCustomerDeviceDetailUseCase.execute(42L, 2001L)).thenReturn(response);

        mockMvc.perform(get("/api/devices/{id}", 2001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(2001))
                .andExpect(jsonPath("$.data.serialNumber").value("LMT-64X64-SEED-0001"))
                .andExpect(jsonPath("$.data.macAddress").value("AA:BB:CC:11:22:01"))
                .andExpect(jsonPath("$.data.deviceName").value("Rear Window Matrix"))
                .andExpect(jsonPath("$.data.status").value("BOUND"))
                .andExpect(jsonPath("$.data.currentFirmwareId").value(12))
                .andExpect(jsonPath("$.data.boundAt").value("2026-11-09T06:00:00Z"))
                .andExpect(jsonPath("$.data.warrantyExpiresAt").value("2027-05-08T06:00:00Z"))
                .andExpect(jsonPath("$.data.warrantyStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.model.modelId").value(101))
                .andExpect(jsonPath("$.data.model.productId").value(301))
                .andExpect(jsonPath("$.data.model.modelName").value("Lumintra Matrix 64x64 Pro"))
                .andExpect(jsonPath("$.data.model.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.model.spec.widthPx").value(64))
                .andExpect(jsonPath("$.data.model.spec.heightPx").value(64))
                .andExpect(jsonPath("$.data.model.spec.attributes[0].label").value("Voltage"))
                .andExpect(jsonPath("$.data.model.spec.attributes[0].value").value("5V USB-C"))
                .andExpect(jsonPath("$.data.fulfillment.orderId").value(55))
                .andExpect(jsonPath("$.data.fulfillment.orderItemId").value(102))
                .andExpect(jsonPath("$.data.qrPayload").doesNotExist())
                .andExpect(jsonPath("$.data.owner").doesNotExist());

        verify(getCustomerDeviceDetailUseCase).execute(42L, 2001L);
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-95 CTRL-18 [B]: GET /api/devices/{id} returns 200 OK with DEPRECATED model, expired warranty, and null fulfillment")
    void getDeviceDetail_deprecatedModelAndNullFulfillment_returns200() throws Exception {
        CustomerDeviceDetailResponse response = CustomerDeviceDetailResponse.builder()
                .deviceId(2002L)
                .serialNumber("LMT-32X16-0002")
                .macAddress("AA:BB:CC:11:22:02")
                .status(LedInstanceStatus.BOUND)
                .warrantyStatus(WarrantyStatus.EXPIRED)
                .model(CustomerDeviceDetailResponse.ModelInfo.builder()
                        .modelId(102L)
                        .modelName("Lumintra Legacy 32x16")
                        .status(LedModelStatus.DEPRECATED)
                        .spec(LedModelListResponse.LedSpec.builder()
                                .widthPx(32)
                                .heightPx(16)
                                .attributes(List.of())
                                .build())
                        .build())
                .build();

        when(getCustomerDeviceDetailUseCase.execute(42L, 2002L)).thenReturn(response);

        mockMvc.perform(get("/api/devices/{id}", 2002L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value(2002))
                .andExpect(jsonPath("$.data.warrantyStatus").value("EXPIRED"))
                .andExpect(jsonPath("$.data.model.status").value("DEPRECATED"))
                .andExpect(jsonPath("$.data.fulfillment").doesNotExist());
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-95 CTRL-19 [A]: GET /api/devices/{id} returns 400 VALIDATION_FAILED for non-positive or non-numeric id")
    void getDeviceDetail_invalidPathId_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/devices/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/devices/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/devices/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(getCustomerDeviceDetailUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("DEV-95 CTRL-20 [A]: GET /api/devices/{id} returns 401 UNAUTHORIZED when unauthenticated")
    void getDeviceDetail_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/devices/{id}", 2001L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(getCustomerDeviceDetailUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "not-a-number", roles = {"CUSTOMER"})
    @DisplayName("DEV-95 CTRL-21 [A]: GET /api/devices/{id} returns 401 INVALID_CREDENTIALS when token principal is invalid")
    void getDeviceDetail_invalidPrincipal_returns401() throws Exception {
        mockMvc.perform(get("/api/devices/{id}", 2001L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verify(getCustomerDeviceDetailUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "42", roles = {"STAFF"})
    @DisplayName("DEV-95 CTRL-22 [A]: GET /api/devices/{id} returns 403 FORBIDDEN for non-CUSTOMER role")
    void getDeviceDetail_nonCustomerRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/devices/{id}", 2001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(getCustomerDeviceDetailUseCase, never()).execute(any(), any());
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-95 CTRL-23 [A]: GET /api/devices/{id} returns 403 DEVICE_OWNERSHIP_MISMATCH when device belongs to another customer")
    void getDeviceDetail_notOwnedByCustomer_returns403DeviceOwnershipMismatch() throws Exception {
        when(getCustomerDeviceDetailUseCase.execute(42L, 2001L))
                .thenThrow(new DeviceOwnershipMismatchException("You do not own this LED hardware device."));

        mockMvc.perform(get("/api/devices/{id}", 2001L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEVICE_OWNERSHIP_MISMATCH"));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-95 CTRL-24 [A]: GET /api/devices/{id} returns 404 LED_INSTANCE_NOT_FOUND when device does not exist")
    void getDeviceDetail_unknownDevice_returns404LedInstanceNotFound() throws Exception {
        when(getCustomerDeviceDetailUseCase.execute(42L, 9999L))
                .thenThrow(new LedInstanceNotFoundException("LED hardware device not found."));

        mockMvc.perform(get("/api/devices/{id}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LED_INSTANCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "42", roles = {"CUSTOMER"})
    @DisplayName("DEV-95 CTRL-25 [A]: GET /api/devices/{id} returns 500 INTERNAL_SERVER_ERROR on unexpected runtime exception")
    void getDeviceDetail_unexpectedFailure_returns500() throws Exception {
        when(getCustomerDeviceDetailUseCase.execute(42L, 2001L))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/devices/{id}", 2001L))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
