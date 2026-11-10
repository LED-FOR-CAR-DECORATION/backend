package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.OrderController;
import com.lumintra.backend.commerce.api.request.CheckoutRequest;
import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.application.CheckoutOrderUseCase;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commerce.AddressNotFoundException;
import com.lumintra.backend.shared.exception.commerce.DeliveryAddressRequiredException;
import com.lumintra.backend.shared.exception.commerce.InsufficientStockException;
import com.lumintra.backend.shared.exception.commerce.InvalidCartItemException;
import com.lumintra.backend.shared.exception.commerce.ProductAlreadyOwnedException;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
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
}
