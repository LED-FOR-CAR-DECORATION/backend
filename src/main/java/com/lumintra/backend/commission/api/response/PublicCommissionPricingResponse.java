package com.lumintra.backend.commission.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Schema(description = "Public response containing commission pricing tier summaries and active configuration rules")
@Builder
public record PublicCommissionPricingResponse(
        @Schema(description = "List of active commission pricing tier summaries including bundle savings")
        List<PricingTierSummaryResponse> tiers,

        @Schema(description = "List of active pricing configuration rules ordered by displayOrder")
        List<PricingRuleResponse> rules
) {}
