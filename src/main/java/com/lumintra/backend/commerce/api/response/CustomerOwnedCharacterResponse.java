package com.lumintra.backend.commerce.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Customer owned character details from store purchases")
public record CustomerOwnedCharacterResponse(
        @Schema(description = "Identifier of the character", example = "5")
        Long characterId,

        @Schema(description = "Name of the character", example = "Samurai Cat")
        String characterName,

        @Schema(description = "Public CDN URL of preview animation GIF/PNG", example = "https://cdn.lumintra.shop/characters/5/preview.gif")
        String previewGifUrl,

        @Schema(description = "Frame count of the animation", example = "16")
        Integer frameCount,

        @Schema(description = "Frame delay in milliseconds", example = "60")
        Integer frameDelayMs,

        @Schema(description = "Identifier of the purchase order", example = "1042")
        Long orderId,

        @Schema(description = "Identifier of the store product purchased", example = "28")
        Long storeProductId,

        @Schema(description = "Store product title at purchase time", example = "Samurai Cat Animated Edition")
        String productName,

        @Schema(description = "Unit price at time of purchase", example = "50000.00")
        BigDecimal purchasedPrice,

        @Schema(description = "Timestamp when the character was granted/purchased", example = "2026-11-15T17:00:00Z")
        Instant purchasedAt
) {}
