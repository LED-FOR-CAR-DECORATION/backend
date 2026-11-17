package com.lumintra.backend.commission.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable snapshot model serialized into commission.price_breakdown_json upon commission creation.
 * Preserves exact resolved tier, options, text values, and historical prices.
 */
public record CommissionPriceBreakdownSnapshot(
        String tierCode,
        String tierName,
        boolean isCustom,
        BigDecimal basePrice,
        BigDecimal discountPercent,
        int maxRevisions,
        List<Step1SelectionSnapshot> step1Selections,
        List<Step2SelectionSnapshot> step2Selections,
        BigDecimal computedInitialPrice,
        BigDecimal totalPrice
) {
    public record Step1SelectionSnapshot(
            Long configId,
            String ruleKey,
            String ruleName,
            Long optionId,
            String optionCode,
            String optionLabel,
            BigDecimal basePrice
    ) {}

    public record Step2SelectionSnapshot(
            Long configId,
            String ruleKey,
            String ruleName,
            List<OptionSnapshot> selectedOptions,
            String textValue,
            BigDecimal subtotal
    ) {}

    public record OptionSnapshot(
            Long optionId,
            String optionCode,
            String optionLabel,
            BigDecimal basePrice
    ) {}
}
