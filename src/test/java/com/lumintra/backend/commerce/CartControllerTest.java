package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.CartController;
import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.ViewCartUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import com.lumintra.backend.store.domain.model.ProductType;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViewCartUseCase viewCartUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("UTCID01 [N]: GET /api/cart as CUSTOMER returns 200 OK with ApiResponse<CartResponse>")
    void viewCart_asCustomer_returns200WithCartItems() throws Exception {
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartResponse.CartItemResponse item = CartResponse.CartItemResponse.builder()
                .cartItemId(501L)
                .productId(12L)
                .productName("LED Matrix Panel 32x32")
                .productType(ProductType.LED)
                .thumbnailUrl("https://img.lumintra.shop/matrix32.png")
                .quantity(2)
                .unitPrice(new BigDecimal("350000.00"))
                .subtotal(new BigDecimal("700000.00"))
                .isAvailable(true)
                .unavailableReason(null)
                .addedAt(now)
                .build();

        CartResponse response = new CartResponse(101L, List.of(item), now);
        when(viewCartUseCase.execute(42L)).thenReturn(response);

        mockMvc.perform(get("/api/cart")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(101))
                .andExpect(jsonPath("$.data.items[0].cartItemId").value(501))
                .andExpect(jsonPath("$.data.items[0].productId").value(12))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(350000.00))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(700000.00))
                .andExpect(jsonPath("$.data.items[0].isAvailable").value(true))
                .andExpect(jsonPath("$.data.items[0].unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(viewCartUseCase).execute(42L);
    }

    @Test
    @DisplayName("UTCID02 [A]: GET /api/cart without authentication returns 401 UNAUTHORIZED ErrorResponse")
    void viewCart_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(viewCartUseCase);
    }

    @Test
    @DisplayName("UTCID03 [A]: GET /api/cart as EMPLOYEE (ROLE_STAFF) is rejected with 403 FORBIDDEN ErrorResponse")
    void viewCart_asEmployee_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(viewCartUseCase);
    }

    @Test
    @DisplayName("UTCID04 [A]: GET /api/cart with malformed non-numeric subject returns 401 INVALID_CREDENTIALS")
    void viewCart_nonNumericPrincipal_returns401() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .with(SecurityMockMvcRequestPostProcessors.user("invalid-id").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(viewCartUseCase);
    }
}
