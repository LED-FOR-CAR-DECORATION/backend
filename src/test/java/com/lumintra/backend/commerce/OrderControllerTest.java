package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.OrderController;
import com.lumintra.backend.commerce.api.request.CheckoutRequest;
import com.lumintra.backend.commerce.api.request.OrderFilterRequest;
import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.api.response.OrderListResponse;
import com.lumintra.backend.commerce.application.CheckoutOrderUseCase;
import com.lumintra.backend.commerce.application.ListOrdersUseCase;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commerce.AddressNotFoundException;
import com.lumintra.backend.shared.exception.commerce.DeliveryAddressRequiredException;
import com.lumintra.backend.shared.exception.commerce.InsufficientStockException;
import com.lumintra.backend.shared.exception.commerce.InvalidCartItemException;
import com.lumintra.backend.shared.exception.commerce.ProductAlreadyOwnedException;
import com.lumintra.backend.shared.response.PaginationMeta;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutOrderUseCase checkoutOrderUseCase;

    @MockitoBean
    private ListOrdersUseCase listOrdersUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("UTCID01 [N]: POST /api/orders/checkout as CUSTOMER returns 201 Created with CheckoutResponse")
    void checkout_asCustomer_returns201CreatedWithOrderSummary() throws Exception {
        Instant expiresAt = Instant.parse("2026-11-10T10:15:00Z");
        CheckoutResponse response = new CheckoutResponse(
                1001L,
                5001L,
                PaymentGateway.SEPAY,
                new BigDecimal("759000.00"),
                OrderStatus.PENDING_PAYMENT,
                expiresAt
        );

        when(checkoutOrderUseCase.execute(eq(42L), any(CheckoutRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [1, 2],
                                  "deliveryAddressId": 10,
                                  "paymentGateway": "SEPAY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(1001))
                .andExpect(jsonPath("$.data.paymentId").value(5001))
                .andExpect(jsonPath("$.data.paymentGateway").value("SEPAY"))
                .andExpect(jsonPath("$.data.totalAmount").value(759000.00))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-11-10T10:15:00Z"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(checkoutOrderUseCase).execute(42L, new CheckoutRequest(List.of(1L, 2L), 10L, null, PaymentGateway.SEPAY));
    }

    @Test
    @DisplayName("UTCID02 [B]: POST /api/orders/checkout fails Bean Validation on empty cartItemIds or ambiguous delivery inputs")
    void checkout_invalidRequestPayloads_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [],
                                  "deliveryAddressId": 10,
                                  "paymentGateway": "SEPAY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("cartItemIds"));

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cartItemIds": [1],
                                  "deliveryAddressId": 10,
                                  "shippingDetails": {
                                    "recipientName": "Nguyen Van A",
                                    "recipientPhone": "0901234567",
                                    "fullAddress": "123 Nguyen Hue, D1, HCMC",
                                    "latitude": 10.776889,
                                    "longitude": 106.700806
                                  },
                                  "paymentGateway": "SEPAY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("deliveryAddressId"));

        verifyNoInteractions(checkoutOrderUseCase);
    }

    @Test
    @DisplayName("UTCID03 [A]: POST /api/orders/checkout maps commerce domain exceptions to 400 and 404 ErrorResponse")
    void checkout_domainExceptions_returnExactHttpStatusesAndCodes() throws Exception {
        when(checkoutOrderUseCase.execute(eq(42L), any(CheckoutRequest.class)))
                .thenThrow(new InvalidCartItemException("Invalid cart items."))
                .thenThrow(new DeliveryAddressRequiredException("Delivery address required."))
                .thenThrow(new AddressNotFoundException("Address not found."))
                .thenThrow(new InsufficientStockException("Out of stock."))
                .thenThrow(new ProductAlreadyOwnedException("Already owned."));

        String body = """
                {
                  "cartItemIds": [1],
                  "deliveryAddressId": 10,
                  "paymentGateway": "SEPAY"
                }
                """;

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CART_ITEM"));

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DELIVERY_ADDRESS_REQUIRED"));

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_ALREADY_OWNED"));
    }

    @Test
    @DisplayName("UTCID04 [A]: POST /api/orders/checkout denies unauthenticated (401) and EMPLOYEE ROLE_STAFF (403)")
    void checkout_unauthorizedAndForbidden_returns401And403() throws Exception {
        String body = """
                {
                  "cartItemIds": [1],
                  "paymentGateway": "SEPAY"
                }
                """;

        mockMvc.perform(post("/api/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(checkoutOrderUseCase);
    }

    @Test
    @DisplayName("COM-07 UTCID01 [N]: GET /api/orders as CUSTOMER returns 200 OK with default pagination and summary cards")
    void listOrders_asCustomerWithDefaultParams_returns200WithEnvelopeAndMeta() throws Exception {
        Instant createdAt = Instant.parse("2026-11-10T16:45:00Z");
        Instant expiresAt = Instant.parse("2026-11-10T17:00:00Z");

        OrderListResponse.OrderSummaryItem item = OrderListResponse.OrderSummaryItem.builder()
                .orderId(102L)
                .orderType(OrderType.MIXED)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("250000.00"))
                .totalItems(2)
                .primaryProductName("Lumintra LED Matrix v1")
                .primaryProductThumbnail("https://cdn.lumintra.com/products/led-thumb.png")
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(listOrdersUseCase.execute(eq(42L), any(OrderFilterRequest.class)))
                .thenReturn(new OrderListResponse(List.of(item), meta));

        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].orderId").value(102))
                .andExpect(jsonPath("$.data[0].orderType").value("MIXED"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[0].totalAmount").value(250000.00))
                .andExpect(jsonPath("$.data[0].totalItems").value(2))
                .andExpect(jsonPath("$.data[0].primaryProductName").value("Lumintra LED Matrix v1"))
                .andExpect(jsonPath("$.data[0].primaryProductThumbnail").value("https://cdn.lumintra.com/products/led-thumb.png"))
                .andExpect(jsonPath("$.data[0].expiresAt").value("2026-11-10T17:00:00Z"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-10T16:45:00Z"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.totalElements").value(1));

        verify(listOrdersUseCase).execute(42L, new OrderFilterRequest(null, null, null, 0, 10));
    }

    @Test
    @DisplayName("COM-07 UTCID02 [N]: GET /api/orders binds status, date range, and pagination query parameters cleanly")
    void listOrders_withStatusAndDateFilters_bindsFilterRequestAndReturnsNullExpiresAtWhenNotPendingPayment() throws Exception {
        Instant createdAt = Instant.parse("2026-11-10T10:00:00Z");
        OrderListResponse.OrderSummaryItem item = OrderListResponse.OrderSummaryItem.builder()
                .orderId(95L)
                .orderType(OrderType.DIGITAL)
                .status(OrderStatus.COMPLETED)
                .totalAmount(new BigDecimal("59000.00"))
                .totalItems(1)
                .primaryProductName("Cyber Dragon")
                .primaryProductThumbnail("https://cdn.lumintra.com/products/dragon.png")
                .expiresAt(null)
                .createdAt(createdAt)
                .build();

        PaginationMeta meta = PaginationMeta.builder()
                .page(1)
                .size(20)
                .totalElements(21)
                .totalPages(2)
                .hasNext(false)
                .hasPrevious(true)
                .build();

        when(listOrdersUseCase.execute(eq(42L), any(OrderFilterRequest.class)))
                .thenReturn(new OrderListResponse(List.of(item), meta));

        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .param("status", "COMPLETED")
                        .param("fromDate", "2026-11-01")
                        .param("toDate", "2026-11-30")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderId").value(95))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].expiresAt").doesNotExist())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(20));

        verify(listOrdersUseCase).execute(
                42L,
                new OrderFilterRequest(
                        OrderStatus.COMPLETED,
                        LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 30),
                        1,
                        20
                )
        );
    }

    @Test
    @DisplayName("COM-07 UTCID03 [B]: GET /api/orders returns 400 VALIDATION_FAILED on invalid size, invalid date range, or invalid status")
    void listOrders_invalidQueryParameters_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("size"));

        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("page"));

        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .param("fromDate", "2026-11-20")
                        .param("toDate", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("fromDate"));

        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .param("status", "UNKNOWN_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("status"));

        verifyNoInteractions(listOrdersUseCase);
    }

    @Test
    @DisplayName("COM-07 UTCID04 [A]: GET /api/orders denies unauthenticated (401), EMPLOYEE ROLE_STAFF (403), and non-numeric principal (401)")
    void listOrders_unauthorizedOrForbidden_returns401And403() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/orders")
                        .with(SecurityMockMvcRequestPostProcessors.user("not-a-number").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(listOrdersUseCase);
    }
}
