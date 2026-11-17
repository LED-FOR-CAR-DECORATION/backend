package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.ModifierType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Schema(description = "Bundled option details inside a pricing tier detail view")
@Builder
public record BundledOptionResponse(
        @Schema(description = "Option ID", example = "1")
        Long optionId,

        @Schema(description = "Configuration rule code", example = "FORMAT_TYPE")
        String ruleKey,

        @Schema(description = "Configuration rule display name", example = "Định dạng hiển thị (Format Type)")
        String configName,

        @Schema(description = "Option code", example = "STATIC_IMAGE")
        String optionCode,

        @Schema(description = "Option display label", example = "Ảnh tĩnh (PNG)")
        String optionLabel,

        @Schema(description = "Pricing modifier calculation type", example = "FLAT_ADD")
        ModifierType modifierType,

        @Schema(description = "Standard individual option price in VND or percentage points", example = "0.00")
        BigDecimal basePrice
) {}
