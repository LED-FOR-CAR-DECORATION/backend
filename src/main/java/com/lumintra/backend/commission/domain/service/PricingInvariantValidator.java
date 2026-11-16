package com.lumintra.backend.commission.domain.service;

import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import com.lumintra.backend.shared.exception.commission.PricingTierInvariantViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PricingInvariantValidator {

    public String normalizeAndValidateRuleKey(String rawCode) {
        String ruleKey = rawCode != null ? rawCode.trim() : "";
        if (ruleKey.isEmpty()) {
            throw new InvalidRuleConfigurationException("Rule code is required.");
        }
        return ruleKey;
    }

    public void validateParentOptionDependency(CommissionPriceOptionEntity parentOption) {
        if (parentOption != null
                && parentOption.getConfig() != null
                && parentOption.getConfig().getDependsOnOptionId() != null) {
            throw new InvalidRuleConfigurationException(
                    "Multi-level conditional dependencies are not supported. Parent rule is already dependent on another option."
            );
        }
    }

    /**
     * Enforces revision limits, mandatory CUSTOM tier rules, and preset pricing invariants.
     */
    public void validateTier(CommissionPricingTierEntity tier, CommissionPricingTierEntity customTier) {
        if (tier == null) {
            throw new InvalidRuleConfigurationException("Tier entity cannot be null.");
        }

        validateRevisionLimit(tier);

        if (tier.isCustom()) {
            if (!tier.isActive()) {
                throw new InvalidRuleConfigurationException("CUSTOM tier cannot be deactivated.");
            }
            if (tier.getBasePrice() == null || tier.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidRuleConfigurationException("CUSTOM tier basePrice must be non-negative.");
            }
            return;
        }

        if (tier.isActive()) {
            if (customTier == null || !customTier.isActive()) {
                throw new InvalidRuleConfigurationException("Active CUSTOM tier reference is required to validate preset tier.");
            }

            validateTierDependencyIntegrity(tier);

            BigDecimal customCalculatedPrice = calculateCustomCalculatedPrice(tier, customTier);
            if (tier.getBasePrice() == null || tier.getBasePrice().compareTo(customCalculatedPrice) >= 0) {
                throw new PricingTierInvariantViolationException(String.format(
                    "Pricing invariant violated for tier '%s': basePrice (%s) must be strictly less than custom calculated price (%s).",
                    tier.getTierCode(), tier.getBasePrice(), customCalculatedPrice
                ));
            }
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
        if ("CUSTOM".equalsIgnoreCase(code) && (rev < 1 || rev > 5)) {
            throw new InvalidRuleConfigurationException("CUSTOM tier max revisions must be between 1 and 5.");
        }
    }

    public BigDecimal calculateCustomCalculatedPrice(CommissionPricingTierEntity tier, CommissionPricingTierEntity customTier) {
        if (customTier == null || customTier.getBasePrice() == null) {
            throw new InvalidRuleConfigurationException("Active CUSTOM tier reference is required to calculate custom price.");
        }
        BigDecimal base = customTier.getBasePrice();
        if (tier == null || tier.getIncludedOptions() == null) {
            return base;
        }
        BigDecimal optionsSum = tier.getIncludedOptions().stream()
                .filter(CommissionPriceOptionEntity::isActive)
                .map(CommissionPriceOptionEntity::getBasePrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return base.add(optionsSum);
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
        return savingsAmount.multiply(new BigDecimal("100"))
                .divide(customCalculatedPrice, 2, RoundingMode.HALF_UP);
    }

    /**
     * Validates compatibility between selectionType and uiType, non-priced text inputs, and default option count.
     */
    public void validateRule(CommissionPriceConfigEntity config) {
        if (config == null) {
            throw new InvalidRuleConfigurationException("Config entity cannot be null.");
        }

        SelectionType sel = config.getSelectionType();
        UiType ui = config.getUiType();

        if (sel == SelectionType.SINGLE_SELECT && (ui != UiType.RADIO && ui != UiType.DROPDOWN)) {
            throw new InvalidRuleConfigurationException("SINGLE_SELECT rules only support RADIO or DROPDOWN UI types.");
        }
        if (sel == SelectionType.MULTI_SELECT && ui != UiType.CHECKBOX) {
            throw new InvalidRuleConfigurationException("MULTI_SELECT rules only support CHECKBOX UI type.");
        }
        if (sel == SelectionType.TEXT_INPUT && ui != UiType.TEXT) {
            throw new InvalidRuleConfigurationException("TEXT_INPUT rules only support TEXT UI type.");
        }

        if (sel == SelectionType.TEXT_INPUT && config.getOptions() != null) {
            boolean hasNonZeroPrice = config.getOptions().stream()
                    .anyMatch(o -> o.getBasePrice() != null && o.getBasePrice().compareTo(BigDecimal.ZERO) > 0);
            if (hasNonZeroPrice) {
                throw new InvalidRuleConfigurationException("TEXT_INPUT rules cannot have priced options.");
            }
        }

        if (sel == SelectionType.SINGLE_SELECT && config.getOptions() != null) {
            long defaultCount = config.getOptions().stream().filter(CommissionPriceOptionEntity::isDefault).count();
            if (defaultCount > 1) {
                throw new InvalidRuleConfigurationException("SINGLE_SELECT rule cannot have multiple default options.");
            }
        }
    }

    /**
     * Validates invariants required when creating a custom commission pricing rule.
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

        if ((config.getSelectionType() == SelectionType.SINGLE_SELECT
                || config.getSelectionType() == SelectionType.MULTI_SELECT) && optionCount < 1) {
            throw new InvalidRuleConfigurationException(
                    String.format("%s rules must have at least one option.", config.getSelectionType())
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
                            String.format("Option price must be non-negative for option: %s", option.getOptionCode().trim())
                    );
                }
            }
        }
    }

    private void validateTierDependencyIntegrity(CommissionPricingTierEntity tier) {
        if (tier.getIncludedOptions() == null) {
            return;
        }

        Set<Long> bundledOptionIds = tier.getIncludedOptions().stream()
                .map(CommissionPriceOptionEntity::getOptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (CommissionPriceOptionEntity option : tier.getIncludedOptions()) {
            if (option.getConfig() != null) {
                Long parentOptionId = option.getConfig().getDependsOnOptionId();
                if (parentOptionId != null && !bundledOptionIds.contains(parentOptionId)) {
                    throw new InvalidRuleConfigurationException(String.format(
                        "Tier '%s' bundles dependent option '%s' without bundling its prerequisite option (ID %d).",
                        tier.getTierCode(), option.getOptionCode(), parentOptionId
                    ));
                }
            }
        }
    }
}
