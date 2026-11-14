package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.OrderController;
import com.lumintra.backend.commerce.api.request.CancelOrderRequest;
import com.lumintra.backend.commerce.api.response.CancelOrderResponse;
import com.lumintra.backend.commerce.application.CancelOrderUseCase;
import com.lumintra.backend.commerce.api.request.CheckoutRequest;
import com.lumintra.backend.commerce.api.request.OrderFilterRequest;
import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.api.response.ConfirmOrderDeliveryResponse;
import com.lumintra.backend.commerce.api.response.OrderDetailResponse;
import com.lumintra.backend.commerce.api.response.OrderListResponse;
import com.lumintra.backend.commerce.application.CheckoutOrderUseCase;
import com.lumintra.backend.commerce.application.ConfirmOrderDeliveryUseCase;
import com.lumintra.backend.commerce.application.GetOrderDetailUseCase;
import com.lumintra.backend.commerce.application.ListOrdersUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commerce.AddressNotFoundException;
import com.lumintra.backend.shared.exception.commerce.DeliveryAddressRequiredException;
import com.lumintra.backend.shared.exception.commerce.InsufficientStockException;
import com.lumintra.backend.shared.exception.commerce.InvalidCartItemException;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.shared.exception.commerce.ProductAlreadyOwnedException;
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
    private GetOrderDetailUseCase getOrderDetailUseCase;

    @MockitoBean
    private ConfirmOrderDeliveryUseCase confirmOrderDeliveryUseCase;

    @MockitoBean
    private CancelOrderUseCase cancelOrderUseCase;

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

    @Test
    @DisplayName("COM-08 UTCID01 [N]: GET /api/orders/{id} for PENDING_PAYMENT MIXED order returns 200 OK with items, delivery, and VietQR instructions")
    void getOrderDetail_pendingMixedOrder_returns200WithDeliveryAndVietQr() throws Exception {
        Instant createdAt = Instant.parse("2026-11-10T16:45:00Z");
        Instant expiresAt = Instant.parse("2026-11-10T17:00:00Z");

        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(102L)
                .orderType(OrderType.MIXED)
                .status(OrderStatus.PENDING_PAYMENT)
                .subtotal(new BigDecimal("250000.00"))
                .shippingFee(new BigDecimal("0.00"))
                .totalAmount(new BigDecimal("250000.00"))
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .items(List.of(
                        OrderDetailResponse.OrderItemDetail.builder()
                                .orderItemId(1L)
                                .productId(5L)
                                .productName("Lumintra LED Matrix v1")
                                .productType(ProductType.LED)
                                .thumbnailUrl("https://cdn.lumintra.com/products/led.png")
                                .previewGifUrl(null)
                                .unitPrice(new BigDecimal("200000.00"))
                                .quantity(1)
                                .subtotal(new BigDecimal("200000.00"))
                                .build(),
                        OrderDetailResponse.OrderItemDetail.builder()
                                .orderItemId(2L)
                                .productId(8L)
                                .productName("Pixel Cat Character")
                                .productType(ProductType.CHARACTER)
                                .thumbnailUrl("https://cdn.lumintra.com/products/cat.png")
                                .previewGifUrl("https://cdn.lumintra.com/characters/cat-preview.gif")
                                .unitPrice(new BigDecimal("50000.00"))
                                .quantity(1)
                                .subtotal(new BigDecimal("50000.00"))
                                .build()
                ))
                .delivery(OrderDetailResponse.DeliveryDetail.builder()
                        .carrier("AHAMOVE")
                        .recipientName("Dang Phuoc Nguyen")
                        .recipientPhone("0912345678")
                        .fullAddress("Saigon Hi-Tech Park, Thu Duc City, HCMC")
                        .latitude(new BigDecimal("10.8411000"))
                        .longitude(new BigDecimal("106.8099000"))
                        .deliveryStatus(DeliveryStatus.PENDING)
                        .trackingNumber(null)
                        .trackingUrl(null)
                        .estimatedDeliveryAt(null)
                        .build())
                .payment(OrderDetailResponse.PaymentDetail.builder()
                        .paymentGateway(PaymentGateway.SEPAY)
                        .status(PaymentStatus.PENDING)
                        .paymentContent("LUM102")
                        .qrCodeUrl("https://qr.sepay.vn/img?acc=0123456789&bank=MBBank&amount=250000&des=LUM102")
                        .bankAccount("0123456789")
                        .bankName("MBBank")
                        .expiresAt(expiresAt)
                        .build())
                .build();

        when(getOrderDetailUseCase.execute(42L, 102L)).thenReturn(response);

        mockMvc.perform(get("/api/orders/102")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(102))
                .andExpect(jsonPath("$.data.orderType").value("MIXED"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(250000.00))
                .andExpect(jsonPath("$.data.items[0].productName").value("Lumintra LED Matrix v1"))
                .andExpect(jsonPath("$.data.items[0].previewGifUrl").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].productType").value("CHARACTER"))
                .andExpect(jsonPath("$.data.items[1].previewGifUrl").value("https://cdn.lumintra.com/characters/cat-preview.gif"))
                .andExpect(jsonPath("$.data.delivery.carrier").value("AHAMOVE"))
                .andExpect(jsonPath("$.data.delivery.latitude").value(10.8411))
                .andExpect(jsonPath("$.data.payment.paymentContent").value("LUM102"))
                .andExpect(jsonPath("$.data.payment.qrCodeUrl").value("https://qr.sepay.vn/img?acc=0123456789&bank=MBBank&amount=250000&des=LUM102"))
                .andExpect(jsonPath("$.data.payment.bankReferenceCode").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(getOrderDetailUseCase).execute(42L, 102L);
    }

    @Test
    @DisplayName("COM-08 UTCID02 [N]: GET /api/orders/{id} for COMPLETED DIGITAL order returns null delivery and settled payment summary")
    void getOrderDetail_completedDigitalOrder_returnsSettledPaymentAndNullDelivery() throws Exception {
        Instant createdAt = Instant.parse("2026-11-10T10:00:00Z");
        Instant paidAt = Instant.parse("2026-11-10T10:02:15Z");

        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(101L)
                .orderType(OrderType.DIGITAL)
                .status(OrderStatus.COMPLETED)
                .subtotal(new BigDecimal("59000.00"))
                .shippingFee(new BigDecimal("0.00"))
                .totalAmount(new BigDecimal("59000.00"))
                .expiresAt(null)
                .createdAt(createdAt)
                .items(List.of(
                        OrderDetailResponse.OrderItemDetail.builder()
                                .orderItemId(10L)
                                .productId(203L)
                                .productName("Cyber Dragon")
                                .productType(ProductType.CHARACTER)
                                .thumbnailUrl("https://cdn.lumintra.com/products/dragon.png")
                                .previewGifUrl("https://cdn.lumintra.com/characters/dragon-preview.gif")
                                .unitPrice(new BigDecimal("59000.00"))
                                .quantity(1)
                                .subtotal(new BigDecimal("59000.00"))
                                .build()
                ))
                .delivery(null)
                .payment(OrderDetailResponse.PaymentDetail.builder()
                        .paymentGateway(PaymentGateway.SEPAY)
                        .status(PaymentStatus.SUCCESS)
                        .bankReferenceCode("SEPAY-FT263149981")
                        .paidAmount(new BigDecimal("59000.00"))
                        .paidAt(paidAt)
                        .build())
                .build();

        when(getOrderDetailUseCase.execute(42L, 101L)).thenReturn(response);

        mockMvc.perform(get("/api/orders/101")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(101))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.data.delivery").doesNotExist())
                .andExpect(jsonPath("$.data.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.payment.bankReferenceCode").value("SEPAY-FT263149981"))
                .andExpect(jsonPath("$.data.payment.paidAmount").value(59000.00))
                .andExpect(jsonPath("$.data.payment.paidAt").value("2026-11-10T10:02:15Z"))
                .andExpect(jsonPath("$.data.payment.qrCodeUrl").doesNotExist());

        verify(getOrderDetailUseCase).execute(42L, 101L);
    }

    @Test
    @DisplayName("COM-08 UTCID03 [B]: GET /api/orders/{id} rejects non-numeric and non-positive path ids with 400 VALIDATION_FAILED")
    void getOrderDetail_invalidPathId_returns400ValidationFailed() throws Exception {
        mockMvc.perform(get("/api/orders/abc")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"));

        mockMvc.perform(get("/api/orders/0")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"));

        mockMvc.perform(get("/api/orders/-5")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("id"));

        verifyNoInteractions(getOrderDetailUseCase);
    }

    @Test
    @DisplayName("COM-08 UTCID04 [A]: GET /api/orders/{id} returns 404 ORDER_NOT_FOUND for missing/IDOR order, and 401/403 for invalid auth")
    void getOrderDetail_notFoundAndSecurityDenials_returnExactErrorResponses() throws Exception {
        when(getOrderDetailUseCase.execute(42L, 999L))
                .thenThrow(new OrderNotFoundException("Order not found."));

        mockMvc.perform(get("/api/orders/999")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found."));

        mockMvc.perform(get("/api/orders/102"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/orders/102")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/orders/102")
                        .with(SecurityMockMvcRequestPostProcessors.user("not-a-number").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("COM-15 UTCID01 [N]: POST /api/orders/{id}/confirm-delivery as CUSTOMER returns 200 OK with ConfirmOrderDeliveryResponse")
    void confirmDelivery_asCustomer_returns200WithUpdatedStatus() throws Exception {
        Instant updatedAt = Instant.parse("2026-11-12T14:25:00Z");
        ConfirmOrderDeliveryResponse response = new ConfirmOrderDeliveryResponse(
                102L,
                OrderStatus.DELIVERED,
                OrderStatus.COMPLETED,
                updatedAt
        );

        when(confirmOrderDeliveryUseCase.execute(42L, 102L)).thenReturn(response);

        mockMvc.perform(post("/api/orders/102/confirm-delivery")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(102))
                .andExpect(jsonPath("$.data.previousStatus").value("DELIVERED"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-12T14:25:00Z"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(confirmOrderDeliveryUseCase).execute(42L, 102L);
    }

    @Test
    @DisplayName("COM-15 UTCID02 [B]: POST /api/orders/{id}/confirm-delivery rejects non-numeric and non-positive ids with 400 VALIDATION_FAILED")
    void confirmDelivery_invalidPathId_returns400ValidationFailed() throws Exception {
        for (String badId : new String[]{"abc", "0", "-5"}) {
            mockMvc.perform(post("/api/orders/" + badId + "/confirm-delivery")
                            .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("id"));
        }

        verifyNoInteractions(confirmOrderDeliveryUseCase);
    }

    @Test
    @DisplayName("COM-15 UTCID03 [A]: POST /api/orders/{id}/confirm-delivery denies unauthenticated (401), non-CUSTOMER roles (403), and non-numeric principal (401)")
    void confirmDelivery_unauthorizedAndForbidden_returns401And403() throws Exception {
        mockMvc.perform(post("/api/orders/102/confirm-delivery"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        for (String role : new String[]{"STAFF", "MANAGER", "ADMIN", "DESIGNER"}) {
            mockMvc.perform(post("/api/orders/102/confirm-delivery")
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        mockMvc.perform(post("/api/orders/102/confirm-delivery")
                        .with(SecurityMockMvcRequestPostProcessors.user("not-a-number").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(confirmOrderDeliveryUseCase);
    }

    @Test
    @DisplayName("COM-15 UTCID04 [A]: POST /api/orders/{id}/confirm-delivery maps 404 ORDER_NOT_FOUND, 409 INVALID_ORDER_STATUS_TRANSITION, and 500 INTERNAL_SERVER_ERROR")
    void confirmDelivery_useCaseExceptions_mapToExpectedErrorResponses() throws Exception {
        when(confirmOrderDeliveryUseCase.execute(42L, 999L))
                .thenThrow(new OrderNotFoundException("Order not found."));

        mockMvc.perform(post("/api/orders/999/confirm-delivery")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found."));

        when(confirmOrderDeliveryUseCase.execute(42L, 102L))
                .thenThrow(new OrderStatusTransitionException(
                        "Cannot confirm delivery for order with status SHIPPING. Only DELIVERED orders can be confirmed."));

        mockMvc.perform(post("/api/orders/102/confirm-delivery")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value(
                        "Cannot confirm delivery for order with status SHIPPING. Only DELIVERED orders can be confirmed."));

        when(confirmOrderDeliveryUseCase.execute(42L, 109L))
                .thenThrow(new IllegalStateException("DB unreachable"));

        mockMvc.perform(post("/api/orders/109/confirm-delivery")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("COM-09 UTCID01 [N]: POST /api/orders/{id}/cancel as CUSTOMER returns 200 OK with CancelOrderResponse (with and without body)")
    void cancel_asCustomer_returns200OkWithSummary() throws Exception {
        Instant updatedAt = Instant.parse("2026-11-13T10:00:00Z");
        CancelOrderResponse response = new CancelOrderResponse(
                101L,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELED,
                updatedAt
        );

        when(cancelOrderUseCase.execute(eq(42L), eq(101L), any())).thenReturn(response);

        // Subcase 1: POST without request body
        mockMvc.perform(post("/api/orders/101/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(101))
                .andExpect(jsonPath("$.data.previousStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-13T10:00:00Z"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        // Subcase 2: POST with valid JSON body containing reason
        CancelOrderResponse refundedResponse = new CancelOrderResponse(
                102L,
                OrderStatus.PROCESSING,
                OrderStatus.REFUNDED,
                updatedAt
        );
        when(cancelOrderUseCase.execute(eq(42L), eq(102L), any())).thenReturn(refundedResponse);

        mockMvc.perform(post("/api/orders/102/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Found a better alternative\"}")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(102))
                .andExpect(jsonPath("$.data.previousStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-13T10:00:00Z"));

        verify(cancelOrderUseCase).execute(eq(42L), eq(101L), any());
        verify(cancelOrderUseCase).execute(eq(42L), eq(102L), any());
    }

    @Test
    @DisplayName("COM-09 UTCID02 [B]: POST /api/orders/{id}/cancel rejects invalid path ID (non-numeric, <= 0) and excessive reason with 400 VALIDATION_FAILED")
    void cancel_invalidPathIdOrBody_returns400ValidationFailed() throws Exception {
        for (String badId : new String[]{"abc", "0", "-5"}) {
            mockMvc.perform(post("/api/orders/" + badId + "/cancel")
                            .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.details[0].field").value("id"));
        }

        String longReason = "a".repeat(501);
        mockMvc.perform(post("/api/orders/101/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"" + longReason + "\"}")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("reason"));

        verifyNoInteractions(cancelOrderUseCase);
    }

    @Test
    @DisplayName("COM-09 UTCID03 [A]: POST /api/orders/{id}/cancel denies unauthenticated (401), non-CUSTOMER roles (403), and non-numeric principal (401)")
    void cancel_unauthorizedAndForbidden_returns401And403() throws Exception {
        mockMvc.perform(post("/api/orders/101/cancel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        for (String role : new String[]{"STAFF", "MANAGER", "ADMIN", "DESIGNER"}) {
            mockMvc.perform(post("/api/orders/101/cancel")
                            .with(SecurityMockMvcRequestPostProcessors.user("7").roles(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        mockMvc.perform(post("/api/orders/101/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user("not-a-number").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(cancelOrderUseCase);
    }

    @Test
    @DisplayName("COM-09 UTCID04 [A]: POST /api/orders/{id}/cancel maps 404 ORDER_NOT_FOUND, 409 INVALID_ORDER_STATUS_TRANSITION, and 500 INTERNAL_SERVER_ERROR")
    void cancel_useCaseExceptions_mapToExpectedErrorResponses() throws Exception {
        when(cancelOrderUseCase.execute(eq(42L), eq(999L), any()))
                .thenThrow(new OrderNotFoundException("Order not found."));

        mockMvc.perform(post("/api/orders/999/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found."));

        when(cancelOrderUseCase.execute(eq(42L), eq(101L), any()))
                .thenThrow(new OrderStatusTransitionException(
                        "Cannot cancel order with status SHIPPING. Only PENDING_PAYMENT or PROCESSING orders can be cancelled."));

        mockMvc.perform(post("/api/orders/101/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value(
                        "Cannot cancel order with status SHIPPING. Only PENDING_PAYMENT or PROCESSING orders can be cancelled."));

        when(cancelOrderUseCase.execute(eq(42L), eq(102L), any()))
                .thenThrow(new IllegalStateException("Database connection pool exhausted"));

        mockMvc.perform(post("/api/orders/102/cancel")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}