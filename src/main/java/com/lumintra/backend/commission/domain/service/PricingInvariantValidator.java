package com.lumintra.backend.commission.domain.service;

import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PricingInvariantValidator {

    public static final int MAX_DEPENDENCY_DEPTH = 5;
    public static final int MIN_REVISIONS = 1;
    public static final int MAX_REVISIONS = 5;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_PERCENT_VALUE = new BigDecimal("1000.00");

    public String normalizeAndValidateRuleKey(String rawCode) {
        String ruleKey = rawCode != null ? rawCode.trim() : "";
        if (ruleKey.isEmpty()) {
            throw new InvalidRuleConfigurationException("Rule code is required.");
        }
        return ruleKey;
    }

    /**
     * Validates multi-level acyclic conditional dependency up to {@link #MAX_DEPENDENCY_DEPTH} levels.
     */
    public void validateParentOptionDependency(
            Long currentConfigId,
            CommissionPriceOptionEntity parentOption,
            Function<Long, CommissionPriceOptionEntity> optionLookup) {
        validateParentOptionDependency(currentConfigId, parentOption, optionLookup, 0);
    }

    /**
     * Same as {@link #validateParentOptionDependency(Long, CommissionPriceOptionEntity, Function)} for an existing
     * rule that already has {@code descendantLevels} levels of dependent rules below it: the ancestor chain plus
     * those levels must stay within {@link #MAX_DEPENDENCY_DEPTH}.
     */
    public void validateParentOptionDependency(
            Long currentConfigId,
            CommissionPriceOptionEntity parentOption,
            Function<Long, CommissionPriceOptionEntity> optionLookup,
            int descendantLevels) {
        if (parentOption == null) {
            return;
        }

        CommissionPriceConfigEntity parentConfig = parentOption.getConfig();
        if (parentConfig == null) {
            throw new InvalidRuleConfigurationException("Parent option must belong to a valid pricing rule.");
        }
        if (parentConfig.getSelectionType() != SelectionType.SINGLE_SELECT
                && parentConfig.getSelectionType() != SelectionType.MULTI_SELECT) {
            throw new InvalidRuleConfigurationException(
                    "Conditional dependencies can only target options from SINGLE_SELECT or MULTI_SELECT rules."
            );
        }

        Set<Long> visitedConfigIds = new HashSet<>();
        if (currentConfigId != null) {
            visitedConfigIds.add(currentConfigId);
        }

        CommissionPriceOptionEntity cursor = parentOption;
        int depth = 0;

        while (cursor != null) {
            depth++;
            if (depth + descendantLevels > MAX_DEPENDENCY_DEPTH) {
                throw new InvalidRuleConfigurationException(
                        String.format("Conditional dependency depth exceeds maximum allowed depth of %d.", MAX_DEPENDENCY_DEPTH)
                );
            }

            CommissionPriceConfigEntity cursorConfig = cursor.getConfig();
            if (cursorConfig != null && cursorConfig.getConfigId() != null) {
                if (!visitedConfigIds.add(cursorConfig.getConfigId())) {
                    throw new InvalidRuleConfigurationException(
                            "Circular conditional dependency detected in pricing rule hierarchy."
                    );
                }
            }

            Long nextParentOptionId = cursorConfig != null ? cursorConfig.getDependsOnOptionId() : null;
            if (nextParentOptionId == null) {
                break;
            }
            if (Objects.equals(nextParentOptionId, cursor.getOptionId())) {
                throw new InvalidRuleConfigurationException(
                        "Circular conditional dependency detected in pricing rule hierarchy."
                );
            }

            cursor = optionLookup != null ? optionLookup.apply(nextParentOptionId) : null;
        }
    }

    /**
     * Enforces revision limits, mandatory CUSTOM tier rules, non-negative preset basePrice,
     * and transitive dependency integrity (without forcing CUSTOM price > preset price).
     */
    public void validateTier(CommissionPricingTierEntity tier, CommissionPricingTierEntity customTier) {
        if (tier == null) {
            throw new InvalidRuleConfigurationException("Tier entity cannot be null.");
        }

        validateRevisionLimit(tier);

        if (tier.getBasePrice() == null || tier.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRuleConfigurationException(
                    String.format("Tier '%s' basePrice must be non-negative.", tier.getTierCode())
            );
        }

        if (tier.isCustom()) {
            if (!tier.isActive()) {
                throw new InvalidRuleConfigurationException("CUSTOM tier cannot be deactivated.");
            }
            return;
        }

        if (tier.isActive()) {
            validateTierDependencyIntegrity(tier);
        }
    }

    public void validateRevisionLimit(CommissionPricingTierEntity tier) {
        String code = tier.getTierCode();
        int rev = tier.getMaxRevisions();
        if ("BASIC".equalsIgnoreCase(code) && rev != 1) {
            throw new InvalidRuleConfigurationException("BASIC tier must have exactly 1 max revision.");
        }
        if ("ADVANCED".equalsIgnoreCase(code) && rev != 3) {
            throw new InvalidRuleConfigurationException("ADVANCED tier must have exactly 3 max revisions.");
        }
        if ("CUSTOM".equalsIgnoreCase(code) && (rev < MIN_REVISIONS || rev > MAX_REVISIONS)) {
            throw new InvalidRuleConfigurationException("CUSTOM tier max revisions must be between 1 and 5.");
        }
        if (rev < MIN_REVISIONS || rev > MAX_REVISIONS) {
            throw new InvalidRuleConfigurationException(String.format(
                    "Tier '%s' max revisions must be between %d and %d.", code, MIN_REVISIONS, MAX_REVISIONS));
        }
    }

    /**
     * Line item evaluated by the deterministic 4-stage modifier pipeline.
     */
    public record ModifierLineInput(
            CommissionPriceConfigEntity config,
            CommissionPriceOptionEntity option,
            int quantity
    ) {}

    /**
     * Breakdown produced by {@link #calculateModifierPipeline(BigDecimal, List)}.
     */
    public record ModifierCalculationResult(
            BigDecimal baseBucketTotal,
            BigDecimal finalTotal,
            List<BigDecimal> lineAmounts
    ) {}

    /**
     * Calculates what the same bundled option selection would cost under CUSTOM mode using the
     * 3-modifier pipeline: Base -> BASE_PERCENT -> non-base FLAT_ADD -> SUBTOTAL_PERCENT.
     */
    public BigDecimal calculateCustomCalculatedPrice(CommissionPricingTierEntity tier, CommissionPricingTierEntity customTier) {
        if (customTier == null || customTier.getBasePrice() == null) {
            throw new InvalidRuleConfigurationException("Active CUSTOM tier reference is required to calculate custom price.");
        }
        BigDecimal customBase = customTier.getBasePrice();
        if (tier == null || tier.getIncludedOptions() == null || tier.getIncludedOptions().isEmpty()) {
            return customBase;
        }

        List<ModifierLineInput> lines = tier.getIncludedOptions().stream()
                .filter(Objects::nonNull)
                .filter(CommissionPriceOptionEntity::isActive)
                .map(opt -> new ModifierLineInput(opt.getConfig(), opt, 1))
                .toList();

        return calculateModifierPipeline(customBase, lines).finalTotal();
    }

    /**
     * Evaluates selected/charged option lines across the deterministic 4-stage modifier pipeline:
     * Base (= startingBase + isBasePrice FLAT_ADD) -> + BASE_PERCENT of Base
     * -> + non-base FLAT_ADD (including QUANTITY unit*qty) = RunningSubtotal -> + SUBTOTAL_PERCENT of RunningSubtotal.
     */
    public ModifierCalculationResult calculateModifierPipeline(BigDecimal startingBase, List<ModifierLineInput> lines) {
        BigDecimal resolvedStart = startingBase != null ? startingBase : BigDecimal.ZERO;
        if (lines == null || lines.isEmpty()) {
            BigDecimal zeroScaled = resolvedStart.setScale(2, RoundingMode.HALF_UP);
            return new ModifierCalculationResult(zeroScaled, zeroScaled, List.of());
        }

        BigDecimal[] computed = new BigDecimal[lines.size()];
        Arrays.fill(computed, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        BigDecimal baseAddons = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            ModifierLineInput line = lines.get(i);
            CommissionPriceOptionEntity opt = line.option();
            CommissionPriceConfigEntity cfg = line.config() != null ? line.config() : (opt != null ? opt.getConfig() : null);
            if (opt != null && opt.resolvedModifierType() == ModifierType.FLAT_ADD && cfg != null && cfg.isBasePrice()) {
                BigDecimal amount = multiplyOptionAmount(opt, line.quantity());
                computed[i] = amount;
                baseAddons = baseAddons.add(amount);
            }
        }

        BigDecimal baseTotal = resolvedStart.add(baseAddons).setScale(2, RoundingMode.HALF_UP);

        BigDecimal basePercentAddons = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            CommissionPriceOptionEntity opt = lines.get(i).option();
            if (opt != null && opt.resolvedModifierType() == ModifierType.BASE_PERCENT) {
                BigDecimal pct = opt.getBasePrice() != null ? opt.getBasePrice() : BigDecimal.ZERO;
                BigDecimal amount = baseTotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                computed[i] = amount;
                basePercentAddons = basePercentAddons.add(amount);
            }
        }

        BigDecimal nonBaseFlatAddons = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            ModifierLineInput line = lines.get(i);
            CommissionPriceOptionEntity opt = line.option();
            CommissionPriceConfigEntity cfg = line.config() != null ? line.config() : (opt != null ? opt.getConfig() : null);
            if (opt != null && opt.resolvedModifierType() == ModifierType.FLAT_ADD && (cfg == null || !cfg.isBasePrice())) {
                BigDecimal amount = multiplyOptionAmount(opt, line.quantity());
                computed[i] = amount;
                nonBaseFlatAddons = nonBaseFlatAddons.add(amount);
            }
        }

        BigDecimal runningSubtotal = baseTotal.add(basePercentAddons).add(nonBaseFlatAddons);

        BigDecimal subtotalPercentAddons = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            CommissionPriceOptionEntity opt = lines.get(i).option();
            if (opt != null && opt.resolvedModifierType() == ModifierType.SUBTOTAL_PERCENT) {
                BigDecimal pct = opt.getBasePrice() != null ? opt.getBasePrice() : BigDecimal.ZERO;
                BigDecimal amount = runningSubtotal.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                computed[i] = amount;
                subtotalPercentAddons = subtotalPercentAddons.add(amount);
            }
        }

        BigDecimal finalTotal = runningSubtotal.add(subtotalPercentAddons).setScale(2, RoundingMode.HALF_UP);
        return new ModifierCalculationResult(baseTotal, finalTotal, List.of(computed));
    }

    private BigDecimal multiplyOptionAmount(CommissionPriceOptionEntity opt, int quantity) {
        BigDecimal unit = opt.getBasePrice() != null ? opt.getBasePrice() : BigDecimal.ZERO;
        int effectiveQty = Math.max(0, quantity);
        return unit.multiply(BigDecimal.valueOf(effectiveQty)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSavingsAmount(BigDecimal customCalculatedPrice, BigDecimal finalTierPrice) {
        if (customCalculatedPrice == null || finalTierPrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal savings = customCalculatedPrice.subtract(finalTierPrice);
        return savings.compareTo(BigDecimal.ZERO) > 0 ? savings : BigDecimal.ZERO;
    }

    public BigDecimal calculateDiscountPercent(BigDecimal savingsAmount, BigDecimal customCalculatedPrice) {
        if (customCalculatedPrice == null || savingsAmount == null
                || customCalculatedPrice.compareTo(BigDecimal.ZERO) <= 0
                || savingsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return savingsAmount.multiply(HUNDRED)
                .divide(customCalculatedPrice, 2, RoundingMode.HALF_UP);
    }

    /**
     * Validates primitive/UI compatibility, base-bucket flags, quantity bounds, option modifiers, and defaults.
     */
    public void validateRule(CommissionPriceConfigEntity config) {
        if (config == null) {
            throw new InvalidRuleConfigurationException("Config entity cannot be null.");
        }

        SelectionType sel = config.getSelectionType();
        UiType ui = config.getUiType();

        validateUiCompatibility(sel, ui);
        validateBasePriceEligibility(config, sel);
        validateQuantityBounds(config, sel);
        validateOptionsForSelectionType(config, sel);
    }

    /**
     * Validates all invariants required when creating a custom commission pricing rule.
     */
    public void validateCustomRuleCreation(CommissionPriceConfigEntity config) {
        if (config == null) {
            throw new InvalidRuleConfigurationException("Config entity cannot be null.");
        }

        if (config.isSystem()) {
            throw new InvalidRuleConfigurationException("Custom pricing rules must have isSystem set to false.");
        }

        validateRule(config);

        List<CommissionPriceOptionEntity> options = config.getOptions();
        int optionCount = options != null ? options.size() : 0;
        SelectionType sel = config.getSelectionType();

        if ((sel == SelectionType.SINGLE_SELECT || sel == SelectionType.MULTI_SELECT) && optionCount < 1) {
            throw new InvalidRuleConfigurationException(
                    String.format("%s rules must have at least one option.", sel)
            );
        }
        if (sel == SelectionType.QUANTITY && optionCount != 1) {
            throw new InvalidRuleConfigurationException(
                    "QUANTITY rules must define exactly one unit rate option."
            );
        }

        if (options != null && !options.isEmpty()) {
            Set<String> seenOptionCodes = new HashSet<>();
            for (CommissionPriceOptionEntity option : options) {
                if (option.getOptionCode() == null || option.getOptionCode().trim().isEmpty()) {
                    throw new InvalidRuleConfigurationException("Option code cannot be blank.");
                }
                String normalizedCode = option.getOptionCode().trim().toLowerCase();
                if (!seenOptionCodes.add(normalizedCode)) {
                    throw new InvalidRuleConfigurationException(
                            String.format("Duplicate option code found within rule: %s", option.getOptionCode().trim())
                    );
                }
                if (option.getBasePrice() != null && option.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
                    throw new InvalidRuleConfigurationException(
                            String.format("Option price/rate must be non-negative for option: %s", option.getOptionCode().trim())
                    );
                }
            }
        }
    }

    private void validateUiCompatibility(SelectionType sel, UiType ui) {
        if (sel == SelectionType.SINGLE_SELECT && (ui != UiType.RADIO && ui != UiType.DROPDOWN)) {
            throw new InvalidRuleConfigurationException("SINGLE_SELECT rules only support RADIO or DROPDOWN UI types.");
        }
        if (sel == SelectionType.MULTI_SELECT && ui != UiType.CHECKBOX) {
            throw new InvalidRuleConfigurationException("MULTI_SELECT rules only support CHECKBOX UI type.");
        }
        if (sel == SelectionType.QUANTITY && ui != UiType.NUMBER) {
            throw new InvalidRuleConfigurationException("QUANTITY rules only support NUMBER UI type.");
        }
        if (sel == SelectionType.TEXT_INPUT && ui != UiType.TEXT) {
            throw new InvalidRuleConfigurationException("TEXT_INPUT rules only support TEXT UI type.");
        }
    }

    private void validateBasePriceEligibility(CommissionPriceConfigEntity config, SelectionType sel) {
        if (!config.isBasePrice()) {
            return;
        }
        if (sel != SelectionType.SINGLE_SELECT && sel != SelectionType.MULTI_SELECT) {
            throw new InvalidRuleConfigurationException(
                    "isBasePrice can only be enabled on SINGLE_SELECT or MULTI_SELECT rules."
            );
        }
        if (config.getOptions() != null) {
            boolean hasNonFlatModifier = config.getOptions().stream()
                    .anyMatch(o -> o.resolvedModifierType() != ModifierType.FLAT_ADD);
            if (hasNonFlatModifier) {
                throw new InvalidRuleConfigurationException(
                        "Rules marked with isBasePrice only allow FLAT_ADD options."
                );
            }
        }
    }

    private void validateQuantityBounds(CommissionPriceConfigEntity config, SelectionType sel) {
        Integer minQ = config.getMinQuantity();
        Integer maxQ = config.getMaxQuantity();

        if (sel == SelectionType.QUANTITY) {
            if (minQ == null || maxQ == null) {
                throw new InvalidRuleConfigurationException(
                        "QUANTITY rules require both minQuantity and maxQuantity."
                );
            }
            if (minQ < 0) {
                throw new InvalidRuleConfigurationException("minQuantity must be greater than or equal to 0.");
            }
            if (maxQ < 1 || maxQ < minQ) {
                throw new InvalidRuleConfigurationException(
                        "maxQuantity must be at least 1 and greater than or equal to minQuantity."
                );
            }
        } else if (minQ != null || maxQ != null) {
            throw new InvalidRuleConfigurationException(
                    "minQuantity and maxQuantity are only supported for QUANTITY rules."
            );
        }
    }

    private void validateOptionsForSelectionType(CommissionPriceConfigEntity config, SelectionType sel) {
        List<CommissionPriceOptionEntity> options = config.getOptions();
        if (options == null) {
            return;
        }

        if (sel == SelectionType.TEXT_INPUT) {
            boolean hasPriceOrPercent = options.stream()
                    .anyMatch(o -> o.resolvedModifierType() != ModifierType.FLAT_ADD
                            || (o.getBasePrice() != null && o.getBasePrice().compareTo(BigDecimal.ZERO) > 0));
            if (hasPriceOrPercent) {
                throw new InvalidRuleConfigurationException("TEXT_INPUT rules cannot have priced or percentage options.");
            }
        }

        if (sel == SelectionType.QUANTITY) {
            boolean hasPercentModifier = options.stream()
                    .anyMatch(o -> o.resolvedModifierType() != ModifierType.FLAT_ADD);
            if (hasPercentModifier) {
                throw new InvalidRuleConfigurationException("QUANTITY rules only support FLAT_ADD unit pricing.");
            }
        }

        if (sel == SelectionType.SINGLE_SELECT) {
            long defaultCount = options.stream().filter(CommissionPriceOptionEntity::isDefault).count();
            if (defaultCount > 1) {
                throw new InvalidRuleConfigurationException("SINGLE_SELECT rule cannot have multiple default options.");
            }
        }

        for (CommissionPriceOptionEntity opt : options) {
            ModifierType mt = opt.resolvedModifierType();
            if ((mt == ModifierType.BASE_PERCENT || mt == ModifierType.SUBTOTAL_PERCENT)
                    && opt.getBasePrice() != null
                    && opt.getBasePrice().compareTo(MAX_PERCENT_VALUE) > 0) {
                throw new InvalidRuleConfigurationException(
                        String.format("Percentage modifier exceeds maximum allowed value (%s%%) for option: %s",
                                MAX_PERCENT_VALUE, opt.getOptionCode())
                );
            }
        }
    }

    /**
     * Every bundled option's whole prerequisite chain (parent option, grandparent option, ... up to
     * {@link #MAX_DEPENDENCY_DEPTH} levels) must be bundled or listed as a trigger option of the same tier.
     */
    private void validateTierDependencyIntegrity(CommissionPricingTierEntity tier) {
        if (tier.getIncludedOptions() == null || tier.getIncludedOptions().isEmpty()) {
            return;
        }

        Map<Long, CommissionPriceOptionEntity> satisfied = new HashMap<>();
        Set<CommissionPriceOptionEntity> triggers = tier.getTriggerOptions() != null
                ? tier.getTriggerOptions()
                : Collections.emptySet();
        for (CommissionPriceOptionEntity option : tier.getIncludedOptions()) {
            if (option != null && option.getOptionId() != null) satisfied.put(option.getOptionId(), option);
        }
        for (CommissionPriceOptionEntity option : triggers) {
            if (option != null && option.getOptionId() != null) satisfied.put(option.getOptionId(), option);
        }

        for (CommissionPriceOptionEntity option : tier.getIncludedOptions()) {
            if (option != null) {
                validatePrerequisiteChain(tier.getTierCode(), option, satisfied);
            }
        }
    }

    private void validatePrerequisiteChain(
            String tierCode, CommissionPriceOptionEntity option, Map<Long, CommissionPriceOptionEntity> satisfied) {
        Set<Long> visitedConfigIds = new HashSet<>();
        CommissionPriceOptionEntity cursor = option;
        int depth = 0;
        while (cursor != null && cursor.getConfig() != null) {
            CommissionPriceConfigEntity cursorConfig = cursor.getConfig();
            if (cursorConfig.getConfigId() != null && !visitedConfigIds.add(cursorConfig.getConfigId())) {
                throw new InvalidRuleConfigurationException(
                        "Circular conditional dependency detected in pricing rule hierarchy.");
            }
            Long parentOptionId = cursorConfig.getDependsOnOptionId();
            if (parentOptionId == null) {
                return;
            }
            if (++depth > MAX_DEPENDENCY_DEPTH) {
                throw new InvalidRuleConfigurationException(String.format(
                        "Tier '%s' bundles option '%s' whose prerequisite chain exceeds the maximum depth of %d.",
                        tierCode, option.getOptionCode(), MAX_DEPENDENCY_DEPTH));
            }
            CommissionPriceOptionEntity parent = satisfied.get(parentOptionId);
            if (parent == null) {
                throw new InvalidRuleConfigurationException(String.format(
                        "Tier '%s' bundles dependent option '%s' without bundling or triggering on its prerequisite option (ID %d).",
                        tierCode, option.getOptionCode(), parentOptionId));
            }
            cursor = parent;
        }
    }
}
