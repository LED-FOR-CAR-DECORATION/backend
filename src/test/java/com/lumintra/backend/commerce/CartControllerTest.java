package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.controller.CartController;
import com.lumintra.backend.commerce.api.request.AddCartItemRequest;
import com.lumintra.backend.commerce.api.request.UpdateCartItemQuantityRequest;
import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.AddCartItemUseCase;
import com.lumintra.backend.commerce.application.RemoveCartItemUseCase;
import com.lumintra.backend.commerce.application.UpdateCartItemQuantityUseCase;
import com.lumintra.backend.commerce.application.ViewCartUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commerce.CartItemNotFoundException;
import com.lumintra.backend.shared.exception.commerce.DigitalItemQuantityLimitException;
import com.lumintra.backend.shared.exception.commerce.InsufficientStockException;
import com.lumintra.backend.shared.exception.commerce.ProductAlreadyOwnedException;
import com.lumintra.backend.shared.exception.commerce.ProductNotAvailableException;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private AddCartItemUseCase addCartItemUseCase;

    @MockitoBean
    private UpdateCartItemQuantityUseCase updateCartItemQuantityUseCase;

    @MockitoBean
    private RemoveCartItemUseCase removeCartItemUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("GET /api/cart as CUSTOMER returns 200 OK with ApiResponse<CartResponse>")
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
    @DisplayName("GET /api/cart without authentication returns 401 UNAUTHORIZED ErrorResponse")
    void viewCart_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(viewCartUseCase);
    }

    @Test
    @DisplayName("GET /api/cart as EMPLOYEE (ROLE_STAFF) is rejected with 403 FORBIDDEN ErrorResponse")
    void viewCart_asEmployee_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(viewCartUseCase);
    }

    @Test
    @DisplayName("GET /api/cart with malformed non-numeric subject returns 401 INVALID_CREDENTIALS")
    void viewCart_nonNumericPrincipal_returns401() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .with(SecurityMockMvcRequestPostProcessors.user("invalid-id").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(viewCartUseCase);
    }

    @Test
    @DisplayName("UTCID01 [N]: POST /api/cart/items as CUSTOMER returns 200 OK with updated CartResponse")
    void addItem_asCustomer_returns200WithUpdatedCart() throws Exception {
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartResponse.CartItemResponse item = CartResponse.CartItemResponse.builder()
                .cartItemId(601L)
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
        when(addCartItemUseCase.execute(eq(42L), any(AddCartItemRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 12,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(101))
                .andExpect(jsonPath("$.data.items[0].productId").value(12))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(700000.00))
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(addCartItemUseCase).execute(42L, new AddCartItemRequest(12L, 2));
    }

    @Test
    @DisplayName("UTCID02 [B]: POST /api/cart/items with quantity = 0 fails Bean Validation with 400 VALIDATION_FAILED")
    void addItem_invalidQuantityZero_returns400ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 12,
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0].field").value("quantity"));

        verifyNoInteractions(addCartItemUseCase);
    }

    @Test
    @DisplayName("UTCID03 [A]: POST /api/cart/items maps domain commerce exceptions to 400 ErrorResponse")
    void addItem_domainExceptions_return400WithExactErrorCodes() throws Exception {
        when(addCartItemUseCase.execute(eq(42L), eq(new AddCartItemRequest(99L, 1))))
                .thenThrow(new ProductNotAvailableException("Product not found."));
        when(addCartItemUseCase.execute(eq(42L), eq(new AddCartItemRequest(30L, 1))))
                .thenThrow(new ProductAlreadyOwnedException("Already owned."));
        when(addCartItemUseCase.execute(eq(42L), eq(new AddCartItemRequest(31L, 2))))
                .thenThrow(new DigitalItemQuantityLimitException("Limit 1."));
        when(addCartItemUseCase.execute(eq(42L), eq(new AddCartItemRequest(12L, 50))))
                .thenThrow(new InsufficientStockException("Out of stock."));

        mockMvc.perform(post("/api/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":99,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));

        mockMvc.perform(post("/api/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":30,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_ALREADY_OWNED"));

        mockMvc.perform(post("/api/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":31,\"quantity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DIGITAL_ITEM_QUANTITY_LIMIT"));

        mockMvc.perform(post("/api/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":12,\"quantity\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("UTCID04 [A]: POST /api/cart/items without token returns 401 and ROLE_STAFF returns 403")
    void addItem_unauthorizedAndForbidden_returns401And403() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":12,\"quantity\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/cart/items")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":12,\"quantity\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(addCartItemUseCase);
    }

    @Test
    @DisplayName("COM-03 UTCID01 [N]: PUT /api/cart/items/{itemId} as CUSTOMER returns 200 OK with updated CartResponse")
    void updateItemQuantity_asCustomer_returns200WithUpdatedCart() throws Exception {
        Instant addedAt = Instant.parse("2026-11-10T02:15:00Z");
        Instant updatedAt = Instant.parse("2026-11-11T15:00:00Z");
        CartResponse.CartItemResponse item = CartResponse.CartItemResponse.builder()
                .cartItemId(501L)
                .productId(12L)
                .productName("LED Matrix Panel 32x32")
                .productType(ProductType.LED)
                .thumbnailUrl("https://img.lumintra.shop/matrix32.png")
                .quantity(3)
                .unitPrice(new BigDecimal("350000.00"))
                .subtotal(new BigDecimal("1050000.00"))
                .isAvailable(true)
                .unavailableReason(null)
                .addedAt(addedAt)
                .build();

        CartResponse response = new CartResponse(101L, List.of(item), updatedAt);
        when(updateCartItemQuantityUseCase.execute(eq(42L), eq(501L), any(UpdateCartItemQuantityRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(101))
                .andExpect(jsonPath("$.data.items[0].cartItemId").value(501))
                .andExpect(jsonPath("$.data.items[0].productId").value(12))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(350000.00))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(1050000.00))
                .andExpect(jsonPath("$.data.items[0].isAvailable").value(true))
                .andExpect(jsonPath("$.data.items[0].unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-11T15:00:00Z"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(updateCartItemQuantityUseCase).execute(42L, 501L, new UpdateCartItemQuantityRequest(3));
    }

    @Test
    @DisplayName("COM-03 UTCID02 [B]: PUT /api/cart/items/{itemId} rejects non-positive/malformed itemId and invalid quantity with 400 VALIDATION_FAILED")
    void updateItemQuantity_invalidPathOrBody_returns400ValidationFailed() throws Exception {
        mockMvc.perform(put("/api/cart/items/abc")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("itemId"));

        mockMvc.perform(put("/api/cart/items/0")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("itemId"));

        mockMvc.perform(put("/api/cart/items/-5")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("itemId"));

        mockMvc.perform(put("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("quantity"));

        mockMvc.perform(put("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": -2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("quantity"));

        mockMvc.perform(put("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("quantity"));

        mockMvc.perform(put("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": \"not-an-int\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(updateCartItemQuantityUseCase);
    }

    @Test
    @DisplayName("COM-03 UTCID03 [A]: PUT /api/cart/items/{itemId} maps 404 CART_ITEM_NOT_FOUND and 400 domain error codes")
    void updateItemQuantity_notFoundAndDomainExceptions_returnExactStatusesAndCodes() throws Exception {
        when(updateCartItemQuantityUseCase.execute(42L, 999L, new UpdateCartItemQuantityRequest(2)))
                .thenThrow(new CartItemNotFoundException("Cart item not found."));
        when(updateCartItemQuantityUseCase.execute(42L, 502L, new UpdateCartItemQuantityRequest(2)))
                .thenThrow(new ProductNotAvailableException("Product is not published for purchase."));
        when(updateCartItemQuantityUseCase.execute(42L, 503L, new UpdateCartItemQuantityRequest(1)))
                .thenThrow(new ProductAlreadyOwnedException("Customer already owns this digital product."));
        when(updateCartItemQuantityUseCase.execute(42L, 504L, new UpdateCartItemQuantityRequest(1)))
                .thenThrow(new DigitalItemQuantityLimitException("Quantity cannot be modified for digital products in the cart."));
        when(updateCartItemQuantityUseCase.execute(42L, 501L, new UpdateCartItemQuantityRequest(50)))
                .thenThrow(new InsufficientStockException("Requested LED quantity exceeds available stock."));

        mockMvc.perform(put("/api/cart/items/999")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Cart item not found."));

        mockMvc.perform(put("/api/cart/items/502")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));

        mockMvc.perform(put("/api/cart/items/503")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_ALREADY_OWNED"));

        mockMvc.perform(put("/api/cart/items/504")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DIGITAL_ITEM_QUANTITY_LIMIT"));

        mockMvc.perform(put("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("COM-03 UTCID04 [A]: PUT /api/cart/items/{itemId} enforces CUSTOMER role and valid numeric principal")
    void updateItemQuantity_unauthorizedForbiddenAndInvalidPrincipal_return401And403() throws Exception {
        mockMvc.perform(put("/api/cart/items/501")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(put("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(put("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("not-a-number").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(updateCartItemQuantityUseCase);
    }

    @Test
    @DisplayName("COM-04 UTCID01 [N]: DELETE /api/cart/items/{itemId} as CUSTOMER returns 200 OK with updated CartResponse")
    void removeItem_asCustomer_returns200WithUpdatedCart() throws Exception {
        Instant addedAt = Instant.parse("2026-11-10T02:15:00Z");
        Instant updatedAt = Instant.parse("2026-11-11T16:00:00Z");
        CartResponse.CartItemResponse remainingItem = CartResponse.CartItemResponse.builder()
                .cartItemId(502L)
                .productId(30L)
                .productName("Cyber Mascot")
                .productType(ProductType.CHARACTER)
                .thumbnailUrl("https://img.lumintra.shop/mascot.png")
                .quantity(1)
                .unitPrice(new BigDecimal("59000.00"))
                .subtotal(new BigDecimal("59000.00"))
                .isAvailable(true)
                .unavailableReason(null)
                .addedAt(addedAt)
                .build();

        when(removeCartItemUseCase.execute(42L, 501L))
                .thenReturn(new CartResponse(101L, List.of(remainingItem), updatedAt));
        when(removeCartItemUseCase.execute(42L, 502L))
                .thenReturn(new CartResponse(101L, List.of(), updatedAt));

        mockMvc.perform(delete("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(101))
                .andExpect(jsonPath("$.data.items[0].cartItemId").value(502))
                .andExpect(jsonPath("$.data.items[0].productType").value("CHARACTER"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-11-11T16:00:00Z"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        mockMvc.perform(delete("/api/cart/items/502")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(101))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(removeCartItemUseCase).execute(42L, 501L);
        verify(removeCartItemUseCase).execute(42L, 502L);
    }

    @Test
    @DisplayName("COM-04 UTCID02 [B]: DELETE /api/cart/items/{itemId} rejects non-numeric and non-positive itemId with 400 VALIDATION_FAILED")
    void removeItem_invalidPathItemId_returns400ValidationFailed() throws Exception {
        mockMvc.perform(delete("/api/cart/items/abc")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("itemId"));

        mockMvc.perform(delete("/api/cart/items/0")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("itemId"));

        mockMvc.perform(delete("/api/cart/items/-5")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("itemId"));

        verifyNoInteractions(removeCartItemUseCase);
    }

    @Test
    @DisplayName("COM-04 UTCID03 [A]: DELETE /api/cart/items/{itemId} returns 404 CART_ITEM_NOT_FOUND when missing or unowned")
    void removeItem_missingOrUnownedItem_returns404CartItemNotFound() throws Exception {
        when(removeCartItemUseCase.execute(42L, 999L))
                .thenThrow(new CartItemNotFoundException("Cart item not found."));

        mockMvc.perform(delete("/api/cart/items/999")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Cart item not found."));
    }

    @Test
    @DisplayName("COM-04 UTCID04 [A]: DELETE /api/cart/items/{itemId} enforces CUSTOMER role and valid numeric principal")
    void removeItem_unauthorizedForbiddenAndInvalidPrincipal_return401And403() throws Exception {
        mockMvc.perform(delete("/api/cart/items/501"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(delete("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/cart/items/501")
                        .with(SecurityMockMvcRequestPostProcessors.user("not-a-number").roles("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        verifyNoInteractions(removeCartItemUseCase);
    }
}
