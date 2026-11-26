package com.lumintra.backend.commission.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Lightweight management summary of a commission pricing preset or CUSTOM mode")
@Builder
public record ManagementPricingTierSummaryResponse(
        @Schema(description = "Unique tier ID", example = "1")
        Long tierId,

        @Schema(description = "Unique tier code identifier", example = "BASIC")
        String tierCode,

        @Schema(description = "Display name of the tier", example = "Gói Cơ Bản (Basic Package)")
        String tierName,

        @Schema(description = "Base tier price in VND", example = "79000.00")
        BigDecimal basePrice,

        @Schema(description = "Informational CUSTOM mode calculated price for identical options in VND", example = "100000.00")
        BigDecimal customCalculatedPrice,

        @Schema(description = "Informational savings amount in VND compared to CUSTOM mode", example = "21000.00")
        BigDecimal savingsAmount,

        @Schema(description = "Included revision quota", example = "1")
        int maxRevisions,

        @Schema(description = "Flag indicating if this is the dynamic CUSTOM configurator", example = "false")
        boolean isCustom,

        @Schema(description = "Whether the tier is active and visible to customers", example = "true")
        boolean isActive,

        @Schema(description = "Display order sequence", example = "1")
        int displayOrder,

        @Schema(description = "Optional option IDs that filter/display this preset (empty applies globally)")
        List<Long> triggerOptionIds,

        @Schema(description = "Number of rule options bundled in this tier", example = "2")
        int includedOptionCount
) {}
