package com.lumintra.backend.commission.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Schema(description = "Commission pricing overview response for store managers and administrators")
@Builder
public record ManagementCommissionPricingResponse(
        @Schema(description = "Summary list of all pricing tiers (preset packages and custom tier, active and inactive)")
        List<ManagementPricingTierSummaryResponse> tiers,

        @Schema(description = "Summary list of all configuration rules (system and custom rules, active and inactive)")
        List<ManagementPricingRuleSummaryResponse> rules
) {}
