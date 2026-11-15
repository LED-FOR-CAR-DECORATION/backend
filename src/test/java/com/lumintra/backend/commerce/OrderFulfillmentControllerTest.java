package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.OrderFulfillmentController;
import com.lumintra.backend.commerce.api.request.LinkOrderFulfillmentDeviceRequest;
import com.lumintra.backend.commerce.api.response.LinkOrderFulfillmentDeviceResponse;
import com.lumintra.backend.commerce.application.LinkOrderFulfillmentDeviceUseCase;
import com.lumintra.backend.commerce.application.UnlinkOrderFulfillmentDeviceUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commerce.OrderFulfillmentException;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderFulfillmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class OrderFulfillmentControllerTest {

    private static final String BASE_PATH = "/api/management/staff/orders";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LinkOrderFulfillmentDeviceUseCase linkOrderFulfillmentDeviceUseCase;

    @MockitoBean
    private UnlinkOrderFulfillmentDeviceUseCase unlinkOrderFulfillmentDeviceUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private LinkOrderFulfillmentDeviceResponse sampleResponse() {
        return new LinkOrderFulfillmentDeviceResponse(
                102L,
                "PARTIALLY_FULFILLED",
                501L,
                204L,
                15L,
                "LMT-64X64-SEED-0001",
                "AA:BB:CC:DD:EE:01",
                1L,
                "Lumintra Matrix 64x64",
                "IN_TRANSIT",
                new LinkOrderFulfillmentDeviceResponse.FulfillmentProgress(1, 2, 1, 3, false),
                Instant.parse("2026-11-15T09:15:00Z")
        );
    }

    @Test
    @DisplayName("COM-16 Controller UTCID01 [P]: STAFF, MANAGER, ADMIN link device and receive 200 OK")
    void linkDevice_allowedRoles_returns200() throws Exception {
        LinkOrderFulfillmentDeviceResponse response = sampleResponse();
        when(linkOrderFulfillmentDeviceUseCase.execute(eq("7"), eq(102L), any())).thenReturn(response);

        for (String role : new String[]{"STAFF", "MANAGER", "ADMIN"}) {
            mockMvc.perform(post(BASE_PATH + "/102/fulfillment-devices")
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "orderItemId": 204,
                                      "serialNumber": "LMT-64X64-SEED-0001"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(102))
                    .andExpect(jsonPath("$.data.orderItemId").value(204))
                    .andExpect(jsonPath("$.data.instanceId").value(15))
                    .andExpect(jsonPath("$.data.instanceStatus").value("IN_TRANSIT"))
                    .andExpect(jsonPath("$.data.progress.orderFulfilledCount").value(1))
                    .andExpect(jsonPath("$.data.progress.isFullyFulfilled").value(false));
        }
    }

    @Test
    @DisplayName("COM-16 Controller UTCID02 [A]: Unauthenticated request returns 401 UNAUTHORIZED")
    void linkDevice_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/102/fulfillment-devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": 204,
                                  "serialNumber": "LMT-001"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("COM-16 Controller UTCID03 [A]: Customer role returns 403 FORBIDDEN")
    void linkDevice_customerRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/102/fulfillment-devices")
                        .with(SecurityMockMvcRequestPostProcessors.user("customer-1").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": 204,
                                  "serialNumber": "LMT-001"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("COM-16 Controller UTCID04 [A]: Validation failure on missing device identifier returns 400 VALIDATION_FAILED")
    void linkDevice_missingIdentifiers_returns400() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/102/fulfillment-devices")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": 204
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("COM-16 Controller UTCID05 [A]: Order not found returns 404 ORDER_NOT_FOUND")
    void linkDevice_orderNotFound_returns404() throws Exception {
        when(linkOrderFulfillmentDeviceUseCase.execute(eq("7"), eq(999L), any()))
                .thenThrow(new OrderNotFoundException("Order not found with id: 999"));

        mockMvc.perform(post(BASE_PATH + "/999/fulfillment-devices")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": 204,
                                  "serialNumber": "LMT-001"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("COM-16 Controller UTCID06 [A]: Order status conflict returns 409 INVALID_ORDER_STATUS_TRANSITION")
    void linkDevice_invalidStatus_returns409() throws Exception {
        when(linkOrderFulfillmentDeviceUseCase.execute(eq("7"), eq(102L), any()))
                .thenThrow(new OrderStatusTransitionException("Order must be in PROCESSING or PARTIALLY_FULFILLED"));

        mockMvc.perform(post(BASE_PATH + "/102/fulfillment-devices")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": 204,
                                  "serialNumber": "LMT-001"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("COM-16 Controller UTCID07 [A]: Device not in stock returns 409 DEVICE_NOT_IN_STOCK")
    void linkDevice_deviceNotInStock_returns409() throws Exception {
        when(linkOrderFulfillmentDeviceUseCase.execute(eq("7"), eq(102L), any()))
                .thenThrow(new OrderFulfillmentException(HttpStatus.CONFLICT, "DEVICE_NOT_IN_STOCK", "Device not in stock"));

        mockMvc.perform(post(BASE_PATH + "/102/fulfillment-devices")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderItemId": 204,
                                  "serialNumber": "LMT-001"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_IN_STOCK"));
    }

    @Test
    @DisplayName("COM-16 Controller UTCID08 [P]: Unlink device returns 200 OK")
    void unlinkDevice_allowedRoles_returns200() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/102/fulfillment-devices/15")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isOk());

        verify(unlinkOrderFulfillmentDeviceUseCase).execute("7", 102L, 15L);
    }
}
