package com.lumintra.backend.commerce.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumintra.backend.store.domain.model.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Active shopping cart response for the authenticated customer")
public record CartResponse(
        @Schema(description = "Unique identifier of the shopping cart", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
        Long cartId,

        @Schema(description = "List of items currently in the shopping cart", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CartItemResponse> items,

        @Schema(description = "ISO-8601 UTC timestamp when the cart was last updated", example = "2026-11-10T03:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {
    @Schema(description = "Line item in the shopping cart with real-time pricing and availability status")
    @Builder
    public record CartItemResponse(
            @Schema(description = "Unique identifier of the cart item", example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
            Long cartItemId,

            @Schema(description = "Product identifier", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
            Long productId,

            @Schema(description = "Current product name", example = "LED Matrix Panel 32x32", requiredMode = Schema.RequiredMode.REQUIRED)
            String productName,

            @Schema(description = "Product type classification", example = "LED", requiredMode = Schema.RequiredMode.REQUIRED)
            ProductType productType,

            @Schema(description = "Product thumbnail image URL", example = "https://img.lumintra.shop/matrix32.png", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String thumbnailUrl,

            @Schema(description = "Requested quantity in cart", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            int quantity,

            @Schema(description = "Current unit price sourced from product base_price", example = "350000.00", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal unitPrice,

            @Schema(description = "Line item subtotal calculated as unitPrice * quantity", example = "700000.00", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal subtotal,

            @JsonProperty("isAvailable")
            @Schema(description = "Indicates whether this item can currently be purchased", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean isAvailable,

            @Schema(description = "Machine-readable unavailability reason when isAvailable is false; null when available", example = "OUT_OF_STOCK", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String unavailableReason,

            @Schema(description = "ISO-8601 UTC timestamp when the item was added to cart", example = "2026-11-10T02:15:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant addedAt
    ) {}
}
