package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.StaffOrderController;
import com.lumintra.backend.commerce.api.request.StaffOrderFilterRequest;
import com.lumintra.backend.commerce.api.response.StaffOrderListResponse;
import com.lumintra.backend.commerce.application.ListStaffOrdersUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
}
