package com.lumintra.backend.commission.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Schema(description = "Management option details including active status, base price, and default selection")
@Builder
public record ManagementPricingOptionResponse(
        @Schema(description = "Unique option ID", example = "1")
        Long optionId,

        @Schema(description = "Unique option code within the rule", example = "STATIC_IMAGE")
        String optionCode,

        @Schema(description = "Display label of the option", example = "Ảnh tĩnh (PNG)")
        String optionLabel,

        @Schema(description = "Price surcharge in VND", example = "0.00")
        BigDecimal basePrice,

        @Schema(description = "Whether option is selected by default", example = "true")
        boolean isDefault,

        @Schema(description = "Whether the option is active and selectable by customers", example = "true")
        boolean isActive,

        @Schema(description = "Display order sequence", example = "1")
        int displayOrder
) {}
