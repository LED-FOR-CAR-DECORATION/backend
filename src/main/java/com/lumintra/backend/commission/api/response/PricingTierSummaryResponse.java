package com.lumintra.backend.commission.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Summary of a commission pricing tier with bundle savings and revision quota")
@Builder
public record PricingTierSummaryResponse(
        @Schema(description = "Unique tier ID", example = "1")
        Long tierId,

        @Schema(description = "Unique tier code identifier", example = "BASIC")
        String tierCode,

        @Schema(description = "Display name of the tier", example = "Gói Cơ Bản (Basic Package)")
        String tierName,

        @Schema(description = "Marketing and deliverable description", example = "Gói thiết kế tĩnh tiêu chuẩn cho ma trận LED nhỏ gọn, tiết kiệm chi phí.")
        String description,

        @Schema(description = "Base tier price in VND", example = "79000.00")
        BigDecimal basePrice,

        @Schema(description = "Calculated custom builder price for identical options in VND", example = "100000.00")
        BigDecimal customCalculatedPrice,

        @Schema(description = "Savings amount in VND compared to custom configurator", example = "21000.00")
        BigDecimal savingsAmount,

        @Schema(description = "Included revision quota", example = "1")
        int maxRevisions,

        @Schema(description = "Flag indicating if this is the dynamic CUSTOM configurator", example = "false")
        boolean isCustom,

        @Schema(description = "Display order sequence", example = "1")
        int displayOrder,

        @Schema(description = "List of option IDs bundled in this tier for client lookup against the rules list")
        List<Long> bundledOptionIds
) {}
