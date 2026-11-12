package com.lumintra.backend.commerce.infrastructure.persistence.mapper;

import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CartMapper {

    public CartResponse toEmptyCartResponse(Instant updatedAt) {
        return new CartResponse(null, Collections.emptyList(), updatedAt);
    }

    public CartResponse toCartResponse(
            CartEntity cart,
            List<CartItemEntity> items,
            Map<Long, ProductSnapshot> productsById,
            Map<Long, AvailabilityCheckResult> availabilityByItemId) {
        if (cart == null) {
            return null;
        }

        List<CartResponse.CartItemResponse> mappedItems = (items == null || items.isEmpty())
                ? Collections.emptyList()
                : items.stream()
                        .map(item -> toCartItemResponse(
                                item,
                                productsById.get(item.getProductId()),
                                availabilityByItemId.get(item.getCartItemId())
                        ))
                        .toList();

        return new CartResponse(cart.getCartId(), mappedItems, cart.getUpdatedAt());
    }

    public CartResponse.CartItemResponse toCartItemResponse(
            CartItemEntity item,
            ProductSnapshot product,
            AvailabilityCheckResult availability) {
        BigDecimal currentUnitPrice = (product != null && product.basePrice() != null)
                ? product.basePrice()
                : item.getUnitPrice();

        BigDecimal subtotal = currentUnitPrice != null
                ? currentUnitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                : BigDecimal.ZERO;

        boolean available = availability != null && availability.available();
        String unavailableReason = availability != null
                ? availability.unavailableReason()
                : AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE;

        return CartResponse.CartItemResponse.builder()
                .cartItemId(item.getCartItemId())
                .productId(item.getProductId())
                .productName(product != null ? product.productName() : "Unavailable Product")
                .productType(product != null ? product.productType() : null)
                .thumbnailUrl(product != null ? product.thumbnailUrl() : null)
                .quantity(item.getQuantity())
                .unitPrice(currentUnitPrice)
                .subtotal(subtotal)
                .isAvailable(available)
                .unavailableReason(unavailableReason)
                .addedAt(item.getAddedAt())
                .build();
    }
}
