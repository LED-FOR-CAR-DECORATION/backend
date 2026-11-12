package com.lumintra.backend.commerce.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload for updating the quantity of an item in the authenticated customer's shopping cart")
public record UpdateCartItemQuantityRequest(
        @Schema(description = "New item quantity to set (minimum 1)", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity
) {}
