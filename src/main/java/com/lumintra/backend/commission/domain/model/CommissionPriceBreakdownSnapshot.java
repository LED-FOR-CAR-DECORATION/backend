package com.lumintra.backend.commission.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable snapshot model serialized into commission.price_breakdown_json upon commission creation.
 * Preserves exact resolved tier, primitive selections, modifiers, quantities, text inputs, and computed line amounts.
 */
public record CommissionPriceBreakdownSnapshot(
        String tierCode,
        String tierName,
        boolean isCustom,
        BigDecimal basePrice,
        BigDecimal discountPercent,
        int maxRevisions,
        List<RuleSelectionSnapshot> selections,
        BigDecimal computedBaseBucket,
        BigDecimal computedInitialPrice,
        BigDecimal totalPrice
) {
    public record RuleSelectionSnapshot(
            Long configId,
            String ruleKey,
            String ruleName,
            SelectionType selectionType,
            boolean isBasePrice,
            Integer quantity,
            String textValue,
            List<OptionSnapshot> selectedOptions,
            BigDecimal computedLineAmount
    ) {}

    public record OptionSnapshot(
            Long optionId,
            String optionCode,
            String optionLabel,
            ModifierType modifierType,
            BigDecimal basePrice,
            boolean includedInPreset,
            BigDecimal computedAmount
    ) {}
}
