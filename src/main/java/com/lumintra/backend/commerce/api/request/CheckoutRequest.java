package com.lumintra.backend.commerce.api.request;

import com.lumintra.backend.commerce.api.request.validation.ValidCheckoutDelivery;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

@ValidCheckoutDelivery
@Schema(description = "Request payload for checking out selected shopping cart items into an order")
public record CheckoutRequest(
        @Schema(description = "Non-empty list of selected cart item IDs belonging to the authenticated customer", example = "[1, 2]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "cartItemIds must not be empty")
        List<@NotNull(message = "cartItemId must not be null") Long> cartItemIds,

        @Schema(description = "Saved address ID from customer's address book (required for physical orders unless shippingDetails is supplied)", example = "10", nullable = true)
        Long deliveryAddressId,

        @Schema(description = "Inline shipping delivery details with GPS coordinates (alternative to deliveryAddressId for physical orders)", nullable = true)
        @Valid
        ShippingDetailsRequest shippingDetails,

        @Schema(description = "Selected payment gateway", example = "SEPAY", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "paymentGateway is required")
        PaymentGateway paymentGateway
) {
    @Schema(description = "Inline shipping recipient and coordinate details")
    public record ShippingDetailsRequest(
            @Schema(description = "Recipient full name", example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "recipientName is required")
            @Size(max = 100, message = "recipientName must not exceed 100 characters")
            String recipientName,

            @Schema(description = "Recipient phone number", example = "0901234567", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "recipientPhone is required")
            @Size(max = 20, message = "recipientPhone must not exceed 20 characters")
            String recipientPhone,

            @Schema(description = "Full shipping street address", example = "123 Nguyen Hue, Ben Nghe, District 1, HCMC", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "fullAddress is required")
            @Size(max = 500, message = "fullAddress must not exceed 500 characters")
            String fullAddress,

            @Schema(description = "Delivery latitude coordinate (-90 to 90)", example = "10.776889", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "latitude is required")
            @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
            @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
            BigDecimal latitude,

            @Schema(description = "Delivery longitude coordinate (-180 to 180)", example = "106.700806", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "longitude is required")
            @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
            @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
            BigDecimal longitude
    ) {}
}
