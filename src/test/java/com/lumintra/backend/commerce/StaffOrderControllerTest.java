package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.StaffOrderController;
import com.lumintra.backend.commerce.api.request.StaffOrderFilterRequest;
import com.lumintra.backend.commerce.api.request.UpdateStaffOrderStatusRequest;
import com.lumintra.backend.commerce.api.response.StaffOrderDetailResponse;
import com.lumintra.backend.commerce.api.response.StaffOrderListResponse;
import com.lumintra.backend.commerce.api.response.StaffOrderStatusResponse;
import com.lumintra.backend.commerce.application.GetStaffOrderDetailUseCase;
import com.lumintra.backend.commerce.application.ListStaffOrdersUseCase;
import com.lumintra.backend.commerce.application.UpdateStaffOrderStatusUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.domain.model.ProductType;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StaffOrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class StaffOrderControllerTest {

    private static final String PATH = "/api/management/staff/orders";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListStaffOrdersUseCase listStaffOrdersUseCase;

    @MockitoBean
    private GetStaffOrderDetailUseCase getStaffOrderDetailUseCase;

    @MockitoBean
    private UpdateStaffOrderStatusUseCase updateStaffOrderStatusUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private StaffOrderListResponse sampleResponse() {
        Instant createdAt = Instant.parse("2026-11-10T16:45:00Z");
        Instant updatedAt = Instant.parse("2026-11-10T16:50:00Z");

        StaffOrderListResponse.StaffOrderSummaryItem item = StaffOrderListResponse.StaffOrderSummaryItem.builder()
                .orderId(102L)
                .orderType(OrderType.MIXED)
                .status(OrderStatus.PROCESSING)
                .paymentStatus(PaymentStatus.SUCCESS)
                .deliveryStatus(DeliveryStatus.PENDING)
                .customerId(15L)
                .customerFullName("Nguyen Van A")
                .customerEmail("customer@example.com")
                .recipientName("Dang Phuoc Nguyen")
                .recipientPhone("0912345678")
                .totalAmount(new BigDecimal("250000.00"))
                .totalItems(2)
                .primaryProductName("Lumintra LED Matrix v1")
                .primaryProductThumbnail("https://cdn.lumintra.com/products/led-thumb.png")
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        return new StaffOrderListResponse(List.of(item), meta);
    }

    @Test
    @DisplayName("COM-10 UTCID01 [N]: STAFF, MANAGER, and ADMIN receive 200 OK with fulfillment queue envelope and customer/delivery enrichment")
    void listOrders_allowedRolesWithDefaultParams_returns200() throws Exception {
        when(listStaffOrdersUseCase.execute(any(), any(StaffOrderFilterRequest.class)))
                .thenReturn(sampleResponse());

        for (String role : new String[]{"STAFF", "MANAGER", "ADMIN"}) {
            mockMvc.perform(get(PATH)
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].orderId").value(102))
                    .andExpect(jsonPath("$.data[0].orderType").value("MIXED"))
                    .andExpect(jsonPath("$.data[0].status").value("PROCESSING"))
                    .andExpect(jsonPath("$.data[0].paymentStatus").value("SUCCESS"))
                    .andExpect(jsonPath("$.data[0].deliveryStatus").value("PENDING"))
                    .andExpect(jsonPath("$.data[0].customerId").value(15))
                    .andExpect(jsonPath("$.data[0].customerFullName").value("Nguyen Van A"))
                    .andExpect(jsonPath("$.data[0].customerEmail").value("customer@example.com"))
                    .andExpect(jsonPath("$.data[0].recipientName").value("Dang Phuoc Nguyen"))
                    .andExpect(jsonPath("$.data[0].recipientPhone").value("0912345678"))
                    .andExpect(jsonPath("$.data[0].totalAmount").value(250000.00))
                    .andExpect(jsonPath("$.data[0].totalItems").value(2))
                    .andExpect(jsonPath("$.data[0].primaryProductName").value("Lumintra LED Matrix v1"))
                    .andExpect(jsonPath("$.data[0].primaryProductThumbnail").value("https://cdn.lumintra.com/products/led-thumb.png"))
                    .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-10T16:45:00Z"))
                    .andExpect(jsonPath("$.data[0].updatedAt").value("2026-11-10T16:50:00Z"))
                    .andExpect(jsonPath("$.meta.page").value(0))
                    .andExpect(jsonPath("$.meta.totalElements").value(1));
        }

        verify(listStaffOrdersUseCase, times(3))
                .execute(eq("7"), eq(new StaffOrderFilterRequest(null, null, null, 0, 10)));
    }

    @Test
    @DisplayName("COM-10 UTCID02 [N]: Query filters for status, paymentStatus, deliveryStatus, and pagination bind cleanly")
    void listOrders_withAllFilters_bindsStaffOrderFilterRequest() throws Exception {
        when(listStaffOrdersUseCase.execute(eq("7"), any(StaffOrderFilterRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .param("status", "PENDING_PAYMENT")
                        .param("paymentStatus", "PENDING")
                        .param("deliveryStatus", "PENDING")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk());

        verify(listStaffOrdersUseCase).execute(
                "7",
                new StaffOrderFilterRequest(
                        OrderStatus.PENDING_PAYMENT,
                        PaymentStatus.PENDING,
                        DeliveryStatus.PENDING,
                        1,
                        20
                )
        );
    }

    @Test
    @DisplayName("COM-10 UTCID03 [B]: Invalid pagination bounds or enum values return 400 VALIDATION_FAILED")
    void listOrders_invalidQueryParams_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .param("page", "1000001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .param("paymentStatus", "PAID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .param("deliveryStatus", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listStaffOrdersUseCase);
    }

    @Test
    @DisplayName("COM-10 UTCID04 [A]: Anonymous callers return 401 and CUSTOMER/DESIGNER roles return 403")
    void listOrders_unauthorizedAndForbiddenRoles_returns401And403() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        for (String role : new String[]{"CUSTOMER", "DESIGNER"}) {
            mockMvc.perform(get(PATH)
                            .with(SecurityMockMvcRequestPostProcessors.user("42").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(listStaffOrdersUseCase);
    }

    @Test
    @DisplayName("COM-10 UTCID05 [A]: Stale DB deactivation or missing principal returns 403 or 401 and storage failures return 500")
    void listOrders_useCaseExceptions_mapTo403_401_and_500() throws Exception {
        when(listStaffOrdersUseCase.execute(eq("7"), any()))
                .thenThrow(new AccessDeniedException("Active internal account is required."));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        when(listStaffOrdersUseCase.execute(eq("bad"), any()))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("bad").roles("STAFF")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        when(listStaffOrdersUseCase.execute(eq("9"), any()))
                .thenThrow(new IllegalStateException("DB unreachable"));

        mockMvc.perform(get(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("9").roles("STAFF")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("COM-11 UTCID01 [N]: STAFF, MANAGER, and ADMIN receive 200 OK with customer summary, items, snapshotted delivery, and payment reconciliation")
    void getOrderDetail_allowedRoles_returns200WithFullDetail() throws Exception {
        Instant createdAt = Instant.parse("2026-11-10T16:45:00Z");
        Instant updatedAt = Instant.parse("2026-11-10T16:50:00Z");
        Instant paidAt = Instant.parse("2026-11-10T16:48:12Z");

        StaffOrderDetailResponse response = StaffOrderDetailResponse.builder()
                .orderId(102L)
                .orderType(OrderType.MIXED)
                .status(OrderStatus.PROCESSING)
                .subtotal(new BigDecimal("250000.00"))
                .shippingFee(new BigDecimal("0.00"))
                .totalAmount(new BigDecimal("250000.00"))
                .expiresAt(null)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .customer(new StaffOrderDetailResponse.CustomerSummary(
                        15L, "Nguyen Van A", "0909111222", "customer@example.com"
                ))
                .items(List.of(
                        StaffOrderDetailResponse.OrderItemDetail.builder()
                                .orderItemId(1L)
                                .productId(5L)
                                .productName("Lumintra LED Matrix v1")
                                .productType(ProductType.LED)
                                .thumbnailUrl("https://cdn.lumintra.com/products/led-v1.png")
                                .previewGifUrl(null)
                                .unitPrice(new BigDecimal("200000.00"))
                                .quantity(1)
                                .subtotal(new BigDecimal("200000.00"))
                                .build()
                ))
                .delivery(StaffOrderDetailResponse.DeliveryDetail.builder()
                        .carrier("AHAMOVE")
                        .recipientName("Dang Phuoc Nguyen")
                        .recipientPhone("0912345678")
                        .fullAddress("Saigon Hi-Tech Park, Thu Duc City, HCMC")
                        .latitude(new BigDecimal("10.8411000"))
                        .longitude(new BigDecimal("106.8099000"))
                        .deliveryStatus(DeliveryStatus.PENDING)
                        .trackingNumber("AHV-2026-99881")
                        .trackingUrl("https://ahamove.com/track/AHV-2026-99881")
                        .carrierFee(new BigDecimal("32000.00"))
                        .estimatedDeliveryAt(null)
                        .deliveredAt(null)
                        .build())
                .payment(StaffOrderDetailResponse.StaffPaymentDetail.builder()
                        .paymentGateway(PaymentGateway.SEPAY)
                        .status(PaymentStatus.SUCCESS)
                        .bankReferenceCode("SEPAY-FT263149981")
                        .paidAmount(new BigDecimal("250000.00"))
                        .paidAt(paidAt)
                        .build())
                .build();

        when(getStaffOrderDetailUseCase.execute("7", 102L)).thenReturn(response);

        for (String role : new String[]{"STAFF", "MANAGER", "ADMIN"}) {
            mockMvc.perform(get(PATH + "/102")
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(102))
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                    .andExpect(jsonPath("$.data.customer.customerId").value(15))
                    .andExpect(jsonPath("$.data.customer.fullName").value("Nguyen Van A"))
                    .andExpect(jsonPath("$.data.customer.phone").value("0909111222"))
                    .andExpect(jsonPath("$.data.customer.email").value("customer@example.com"))
                    .andExpect(jsonPath("$.data.items[0].productName").value("Lumintra LED Matrix v1"))
                    .andExpect(jsonPath("$.data.delivery.recipientName").value("Dang Phuoc Nguyen"))
                    .andExpect(jsonPath("$.data.delivery.fullAddress").value("Saigon Hi-Tech Park, Thu Duc City, HCMC"))
                    .andExpect(jsonPath("$.data.delivery.carrierFee").value(32000.00))
                    .andExpect(jsonPath("$.data.payment.paymentGateway").value("SEPAY"))
                    .andExpect(jsonPath("$.data.payment.bankReferenceCode").value("SEPAY-FT263149981"))
                    .andExpect(jsonPath("$.data.payment.qrCodeUrl").doesNotExist())
                    .andExpect(jsonPath("$.meta").doesNotExist());
        }

        verify(getStaffOrderDetailUseCase, times(3)).execute("7", 102L);
    }

    @Test
    @DisplayName("COM-11 UTCID02 [B]: Invalid path id rejects with 400 VALIDATION_FAILED without calling use case")
    void getOrderDetail_invalidPathId_returns400ValidationFailed() throws Exception {
        for (String badId : new String[]{"abc", "0", "-5"}) {
            mockMvc.perform(get(PATH + "/" + badId)
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("id"));
        }

        verifyNoInteractions(getStaffOrderDetailUseCase);
    }

    @Test
    @DisplayName("COM-11 UTCID03 [A]: Anonymous callers return 401 and CUSTOMER/DESIGNER roles return 403")
    void getOrderDetail_unauthorizedAndForbiddenRoles_returns401And403() throws Exception {
        mockMvc.perform(get(PATH + "/102"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        for (String role : new String[]{"CUSTOMER", "DESIGNER"}) {
            mockMvc.perform(get(PATH + "/102")
                            .with(SecurityMockMvcRequestPostProcessors.user("42").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(getStaffOrderDetailUseCase);
    }

    @Test
    @DisplayName("COM-11 UTCID04 [A]: OrderNotFoundException maps to 404 ORDER_NOT_FOUND and stale/internal errors map to 401/403/500")
    void getOrderDetail_useCaseExceptions_mapTo404_403_401_and_500() throws Exception {
        when(getStaffOrderDetailUseCase.execute("7", 999L))
                .thenThrow(new OrderNotFoundException("Order not found."));

        mockMvc.perform(get(PATH + "/999")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found."));

        when(getStaffOrderDetailUseCase.execute("7", 102L))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        mockMvc.perform(get(PATH + "/102")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        when(getStaffOrderDetailUseCase.execute("bad", 102L))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        mockMvc.perform(get(PATH + "/102")
                        .with(SecurityMockMvcRequestPostProcessors.user("bad").roles("STAFF")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        when(getStaffOrderDetailUseCase.execute("9", 102L))
                .thenThrow(new IllegalStateException("DB timeout"));

        mockMvc.perform(get(PATH + "/102")
                        .with(SecurityMockMvcRequestPostProcessors.user("9").roles("STAFF")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("COM-12 UTCID01 [N]: STAFF, MANAGER, and ADMIN receive 200 OK for COMPLETED, PROCESSING, CANCELED, and CANCELLED alias")
    void updateOrderStatus_allowedRolesAndTargets_returns200() throws Exception {
        Instant updatedAt = Instant.parse("2026-11-12T06:55:00Z");

        when(updateStaffOrderStatusUseCase.execute(
                "7", 102L, new UpdateStaffOrderStatusRequest("COMPLETED")))
                .thenReturn(new StaffOrderStatusResponse(102L, OrderStatus.DELIVERED, OrderStatus.COMPLETED, updatedAt));

        for (String role : new String[]{"STAFF", "MANAGER", "ADMIN"}) {
            mockMvc.perform(patch(PATH + "/102/status")
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"COMPLETED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(102))
                    .andExpect(jsonPath("$.data.previousStatus").value("DELIVERED"))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.updatedAt").value("2026-11-12T06:55:00Z"))
                    .andExpect(jsonPath("$.meta").doesNotExist());
        }

        when(updateStaffOrderStatusUseCase.execute(
                "7", 103L, new UpdateStaffOrderStatusRequest("PROCESSING")))
                .thenReturn(new StaffOrderStatusResponse(103L, OrderStatus.DELIVERY_FAILED, OrderStatus.PROCESSING, updatedAt));

        mockMvc.perform(patch(PATH + "/103/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousStatus").value("DELIVERY_FAILED"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        when(updateStaffOrderStatusUseCase.execute(
                "7", 104L, new UpdateStaffOrderStatusRequest("CANCELLED")))
                .thenReturn(new StaffOrderStatusResponse(104L, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELED, updatedAt));

        mockMvc.perform(patch(PATH + "/104/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("COM-12 UTCID02 [B]: Invalid path id, blank/unsupported target status, and malformed JSON return 400 VALIDATION_FAILED")
    void updateOrderStatus_invalidPathOrBody_returns400ValidationFailed() throws Exception {
        for (String badId : new String[]{"abc", "0", "-5"}) {
            mockMvc.perform(patch(PATH + "/" + badId + "/status")
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"COMPLETED\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("id"));
        }

        for (String invalidBody : new String[]{
                "{}",
                "{\"status\":\"   \"}",
                "{\"status\":\"SHIPPING\"}",
                "{\"status\":\"DELIVERED\"}",
                "{\"status\":\"processing\"}"
        }) {
            mockMvc.perform(patch(PATH + "/102/status")
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("status"));
        }

        mockMvc.perform(patch(PATH + "/102/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(updateStaffOrderStatusUseCase);
    }

    @Test
    @DisplayName("COM-12 UTCID03 [A]: Anonymous callers return 401 and CUSTOMER/DESIGNER roles return 403")
    void updateOrderStatus_unauthorizedAndForbiddenRoles_returns401And403() throws Exception {
        mockMvc.perform(patch(PATH + "/102/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        for (String role : new String[]{"CUSTOMER", "DESIGNER"}) {
            mockMvc.perform(patch(PATH + "/102/status")
                            .with(SecurityMockMvcRequestPostProcessors.user("42").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"COMPLETED\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        verifyNoInteractions(updateStaffOrderStatusUseCase);
    }

    @Test
    @DisplayName("COM-12 UTCID04 [A]: Maps OrderNotFoundException->404, OrderStatusTransitionException->409, and 401/403/500 cleanly")
    void updateOrderStatus_useCaseExceptions_mapToExpectedHttpStatuses() throws Exception {
        when(updateStaffOrderStatusUseCase.execute(eq("7"), eq(999L), any()))
                .thenThrow(new OrderNotFoundException("Order not found."));

        mockMvc.perform(patch(PATH + "/999/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found."));

        when(updateStaffOrderStatusUseCase.execute(eq("7"), eq(102L), any()))
                .thenThrow(new OrderStatusTransitionException("Cannot transition order status from PROCESSING to COMPLETED."));

        mockMvc.perform(patch(PATH + "/102/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Cannot transition order status from PROCESSING to COMPLETED."));

        when(updateStaffOrderStatusUseCase.execute(eq("8"), eq(102L), any()))
                .thenThrow(new AccessDeniedException("Active internal account is required."));

        mockMvc.perform(patch(PATH + "/102/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("8").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        when(updateStaffOrderStatusUseCase.execute(eq("bad"), eq(102L), any()))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        mockMvc.perform(patch(PATH + "/102/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("bad").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        when(updateStaffOrderStatusUseCase.execute(eq("9"), eq(102L), any()))
                .thenThrow(new IllegalStateException("DB unreachable"));

        mockMvc.perform(patch(PATH + "/102/status")
                        .with(SecurityMockMvcRequestPostProcessors.user("9").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}
