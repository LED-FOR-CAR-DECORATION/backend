package com.lumintra.backend.commerce.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload for adding a product to the authenticated customer's shopping cart")
public record AddCartItemRequest(
        @Schema(description = "Identifier of the product to add", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "productId is required")
        Long productId,

        @Schema(description = "Quantity of the product to add (minimum 1)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity
) {}
