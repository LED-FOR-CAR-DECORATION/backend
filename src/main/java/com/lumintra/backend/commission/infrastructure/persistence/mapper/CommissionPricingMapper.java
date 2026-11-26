package com.lumintra.backend.commission.infrastructure.persistence.mapper;

import com.lumintra.backend.commission.api.response.BundledOptionResponse;
import com.lumintra.backend.commission.api.response.ManagementCommissionPricingResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingOptionResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleSummaryResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingTierSummaryResponse;
import com.lumintra.backend.commission.api.response.PricingOptionResponse;
import com.lumintra.backend.commission.api.response.PricingRuleResponse;
import com.lumintra.backend.commission.api.response.PricingTierSummaryResponse;
import com.lumintra.backend.commission.api.response.PublicCommissionPricingResponse;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps persisted commission pricing entities into public and management response DTOs.
 */
@Component
@RequiredArgsConstructor
public class CommissionPricingMapper {

    private final PricingInvariantValidator invariantValidator;

    public PublicCommissionPricingResponse toPublicPricingResponse(
            List<CommissionPricingTierEntity> activeTiers,
            CommissionPricingTierEntity customTier,
            List<CommissionPriceConfigEntity> activeConfigs,
            Map<Long, CommissionPriceOptionEntity> optionLookup) {
        List<PricingTierSummaryResponse> tierResponses = activeTiers == null
                ? List.of()
                : activeTiers.stream()
                        .map(tier -> toTierSummaryResponse(tier, customTier))
                        .toList();

        List<PricingRuleResponse> ruleResponses = activeConfigs == null
                ? List.of()
                : activeConfigs.stream()
                        .map(config -> toPricingRuleResponse(config, optionLookup))
                        .toList();

        return PublicCommissionPricingResponse.builder()
                .tiers(tierResponses)
                .rules(ruleResponses)
                .build();
    }

    public PricingTierSummaryResponse toTierSummaryResponse(
            CommissionPricingTierEntity tier,
            CommissionPricingTierEntity customTier) {
        if (tier == null) {
            return null;
        }

        BigDecimal customCalculatedPrice;
        BigDecimal savingsAmount;
        List<Long> triggerOptionIds = extractSortedOptionIds(tier.getTriggerOptions(), true);
        List<Long> bundledOptionIds;

        if (tier.isCustom()) {
            customCalculatedPrice = tier.getBasePrice() != null ? tier.getBasePrice() : BigDecimal.ZERO;
            savingsAmount = BigDecimal.ZERO;
            bundledOptionIds = List.of();
        } else {
            customCalculatedPrice = invariantValidator.calculateCustomCalculatedPrice(tier, customTier);
            savingsAmount = invariantValidator.calculateSavingsAmount(customCalculatedPrice, tier.getBasePrice());
            bundledOptionIds = extractSortedOptionIds(tier.getIncludedOptions(), true);
        }

        return PricingTierSummaryResponse.builder()
                .tierId(tier.getTierId())
                .tierCode(tier.getTierCode())
                .tierName(tier.getName())
                .description(tier.getDescription())
                .basePrice(tier.getBasePrice())
                .customCalculatedPrice(customCalculatedPrice)
                .savingsAmount(savingsAmount)
                .maxRevisions(tier.getMaxRevisions())
                .isCustom(tier.isCustom())
                .displayOrder(tier.getDisplayOrder())
                .triggerOptionIds(triggerOptionIds)
                .bundledOptionIds(bundledOptionIds)
                .build();
    }

    public PricingRuleResponse toPricingRuleResponse(
            CommissionPriceConfigEntity config,
            Map<Long, CommissionPriceOptionEntity> optionLookup) {
        if (config == null) {
            return null;
        }

        String dependsOnRuleKey = null;
        String dependsOnOptionCode = null;

        if (config.getDependsOnOptionId() != null && optionLookup != null) {
            CommissionPriceOptionEntity parentOption = optionLookup.get(config.getDependsOnOptionId());
            if (parentOption != null) {
                dependsOnOptionCode = parentOption.getOptionCode();
                if (parentOption.getConfig() != null) {
                    dependsOnRuleKey = parentOption.getConfig().getRuleKey();
                }
            }
        }

        List<CommissionPriceOptionEntity> options = config.getOptions() != null
                ? config.getOptions()
                : Collections.emptyList();

        List<PricingOptionResponse> optionResponses = options.stream()
                .filter(CommissionPriceOptionEntity::isActive)
                .sorted(Comparator.comparing(CommissionPriceOptionEntity::getDisplayOrder)
                        .thenComparing(o -> o.getOptionId() != null ? o.getOptionId() : 0L))
                .map(this::toPricingOptionResponse)
                .toList();

        return PricingRuleResponse.builder()
                .configId(config.getConfigId())
                .ruleKey(config.getRuleKey())
                .configName(config.getName())
                .description(config.getDescription())
                .selectionType(config.getSelectionType())
                .uiType(config.getUiType())
                .isBasePrice(config.isBasePrice())
                .minQuantity(config.getMinQuantity())
                .maxQuantity(config.getMaxQuantity())
                .isRequired(config.isRequired())
                .isSystem(config.isSystem())
                .displayOrder(config.getDisplayOrder())
                .dependsOnOptionId(config.getDependsOnOptionId())
                .dependsOnRuleKey(dependsOnRuleKey)
                .dependsOnOptionCode(dependsOnOptionCode)
                .options(optionResponses)
                .build();
    }

    public PricingOptionResponse toPricingOptionResponse(CommissionPriceOptionEntity option) {
        if (option == null) {
            return null;
        }
        return PricingOptionResponse.builder()
                .optionId(option.getOptionId())
                .optionCode(option.getOptionCode())
                .optionLabel(option.getOptionLabel())
                .modifierType(option.resolvedModifierType())
                .basePrice(option.getBasePrice())
                .isDefault(option.isDefault())
                .displayOrder(option.getDisplayOrder())
                .build();
    }

    public ManagementCommissionPricingResponse toManagementPricingResponse(
            List<CommissionPricingTierEntity> allTiers,
            CommissionPricingTierEntity activeCustomTier,
            List<CommissionPriceConfigEntity> allConfigs) {
        List<ManagementPricingTierSummaryResponse> tierSummaries = allTiers == null
                ? List.of()
                : allTiers.stream()
                        .map(tier -> toManagementTierSummary(tier, activeCustomTier))
                        .toList();

        List<ManagementPricingRuleSummaryResponse> ruleSummaries = allConfigs == null
                ? List.of()
                : allConfigs.stream()
                        .map(this::toManagementRuleSummary)
                        .toList();

        return ManagementCommissionPricingResponse.builder()
                .tiers(tierSummaries)
                .rules(ruleSummaries)
                .build();
    }

    public ManagementPricingTierSummaryResponse toManagementTierSummary(
            CommissionPricingTierEntity tier,
            CommissionPricingTierEntity activeCustomTier) {
        if (tier == null) {
            return null;
        }

        BigDecimal customCalculatedPrice;
        BigDecimal savingsAmount;
        List<Long> triggerOptionIds = extractSortedOptionIds(tier.getTriggerOptions(), false);
        int includedOptionCount = tier.isCustom() || tier.getIncludedOptions() == null
                ? 0
                : tier.getIncludedOptions().size();

        if (tier.isCustom()) {
            customCalculatedPrice = tier.getBasePrice() != null ? tier.getBasePrice() : BigDecimal.ZERO;
            savingsAmount = BigDecimal.ZERO;
        } else if (activeCustomTier != null) {
            customCalculatedPrice = invariantValidator.calculateCustomCalculatedPrice(tier, activeCustomTier);
            savingsAmount = invariantValidator.calculateSavingsAmount(customCalculatedPrice, tier.getBasePrice());
        } else {
            customCalculatedPrice = tier.getBasePrice() != null ? tier.getBasePrice() : BigDecimal.ZERO;
            savingsAmount = BigDecimal.ZERO;
        }

        return ManagementPricingTierSummaryResponse.builder()
                .tierId(tier.getTierId())
                .tierCode(tier.getTierCode())
                .tierName(tier.getName())
                .basePrice(tier.getBasePrice())
                .customCalculatedPrice(customCalculatedPrice)
                .savingsAmount(savingsAmount)
                .maxRevisions(tier.getMaxRevisions())
                .isCustom(tier.isCustom())
                .isActive(tier.isActive())
                .displayOrder(tier.getDisplayOrder())
                .triggerOptionIds(triggerOptionIds)
                .includedOptionCount(includedOptionCount)
                .build();
    }

    public ManagementPricingRuleSummaryResponse toManagementRuleSummary(CommissionPriceConfigEntity config) {
        if (config == null) {
            return null;
        }

        int optionCount = config.getOptions() != null ? config.getOptions().size() : 0;

        return ManagementPricingRuleSummaryResponse.builder()
                .configId(config.getConfigId())
                .configCode(config.getRuleKey())
                .configName(config.getName())
                .selectionType(config.getSelectionType())
                .uiType(config.getUiType())
                .isBasePrice(config.isBasePrice())
                .minQuantity(config.getMinQuantity())
                .maxQuantity(config.getMaxQuantity())
                .isRequired(config.isRequired())
                .isSystem(config.isSystem())
                .isActive(config.isActive())
                .displayOrder(config.getDisplayOrder())
                .dependsOnOptionId(config.getDependsOnOptionId())
                .optionCount(optionCount)
                .build();
    }

    public ManagementPricingTierResponse toManagementTierDetail(
            CommissionPricingTierEntity tier,
            CommissionPricingTierEntity activeCustomTier) {
        if (tier == null) {
            return null;
        }

        BigDecimal customCalculatedPrice;
        BigDecimal savingsAmount;
        BigDecimal discountPercent;
        List<Long> triggerOptionIds = extractSortedOptionIds(tier.getTriggerOptions(), false);
        List<BundledOptionResponse> bundledOptions;
        List<Long> bundledOptionIds;

        if (tier.isCustom()) {
            customCalculatedPrice = tier.getBasePrice() != null ? tier.getBasePrice() : BigDecimal.ZERO;
            savingsAmount = BigDecimal.ZERO;
            discountPercent = BigDecimal.ZERO;
            bundledOptions = List.of();
            bundledOptionIds = List.of();
        } else {
            Set<CommissionPriceOptionEntity> options = tier.getIncludedOptions() != null
                    ? tier.getIncludedOptions()
                    : Collections.emptySet();

            bundledOptions = options.stream()
                    .sorted(optionOrdering())
                    .map(this::toBundledOptionResponse)
                    .toList();

            bundledOptionIds = bundledOptions.stream()
                    .map(BundledOptionResponse::optionId)
                    .filter(Objects::nonNull)
                    .toList();

            if (activeCustomTier != null) {
                customCalculatedPrice = invariantValidator.calculateCustomCalculatedPrice(tier, activeCustomTier);
                savingsAmount = invariantValidator.calculateSavingsAmount(customCalculatedPrice, tier.getBasePrice());
                discountPercent = invariantValidator.calculateDiscountPercent(savingsAmount, customCalculatedPrice);
            } else {
                customCalculatedPrice = tier.getBasePrice() != null ? tier.getBasePrice() : BigDecimal.ZERO;
                savingsAmount = BigDecimal.ZERO;
                discountPercent = BigDecimal.ZERO;
            }
        }

        return ManagementPricingTierResponse.builder()
                .tierId(tier.getTierId())
                .tierCode(tier.getTierCode())
                .tierName(tier.getName())
                .description(tier.getDescription())
                .basePrice(tier.getBasePrice())
                .customCalculatedPrice(customCalculatedPrice)
                .savingsAmount(savingsAmount)
                .discountPercent(discountPercent)
                .maxRevisions(tier.getMaxRevisions())
                .extraRevisionFeePercent(tier.getExtraRevisionFeePercent())
                .isCustom(tier.isCustom())
                .isActive(tier.isActive())
                .displayOrder(tier.getDisplayOrder())
                .triggerOptionIds(triggerOptionIds)
                .includedOptions(bundledOptions)
                .includedOptionIds(bundledOptionIds)
                .build();
    }

    public ManagementPricingRuleResponse toManagementRuleDetail(
            CommissionPriceConfigEntity config,
            CommissionPriceOptionEntity parentOption) {
        if (config == null) {
            return null;
        }

        String dependsOnRuleKey = null;
        String dependsOnOptionCode = null;

        if (parentOption != null) {
            dependsOnOptionCode = parentOption.getOptionCode();
            if (parentOption.getConfig() != null) {
                dependsOnRuleKey = parentOption.getConfig().getRuleKey();
            }
        }

        List<CommissionPriceOptionEntity> options = config.getOptions() != null
                ? config.getOptions()
                : Collections.emptyList();

        List<ManagementPricingOptionResponse> optionResponses = options.stream()
                .sorted(Comparator.comparing(CommissionPriceOptionEntity::getDisplayOrder)
                        .thenComparing(o -> o.getOptionId() != null ? o.getOptionId() : 0L))
                .map(this::toManagementOptionResponse)
                .toList();

        return ManagementPricingRuleResponse.builder()
                .configId(config.getConfigId())
                .configCode(config.getRuleKey())
                .configName(config.getName())
                .description(config.getDescription())
                .selectionType(config.getSelectionType())
                .uiType(config.getUiType())
                .isBasePrice(config.isBasePrice())
                .minQuantity(config.getMinQuantity())
                .maxQuantity(config.getMaxQuantity())
                .isRequired(config.isRequired())
                .isSystem(config.isSystem())
                .isActive(config.isActive())
                .displayOrder(config.getDisplayOrder())
                .dependsOnOptionId(config.getDependsOnOptionId())
                .dependsOnRuleKey(dependsOnRuleKey)
                .dependsOnOptionCode(dependsOnOptionCode)
                .options(optionResponses)
                .build();
    }

    public ManagementPricingOptionResponse toManagementOptionResponse(CommissionPriceOptionEntity option) {
        if (option == null) {
            return null;
        }
        return ManagementPricingOptionResponse.builder()
                .optionId(option.getOptionId())
                .optionCode(option.getOptionCode())
                .optionLabel(option.getOptionLabel())
                .modifierType(option.resolvedModifierType())
                .basePrice(option.getBasePrice())
                .isDefault(option.isDefault())
                .isActive(option.isActive())
                .displayOrder(option.getDisplayOrder())
                .build();
    }

    public BundledOptionResponse toBundledOptionResponse(CommissionPriceOptionEntity option) {
        if (option == null) {
            return null;
        }
        return BundledOptionResponse.builder()
                .optionId(option.getOptionId())
                .ruleKey(option.getConfig() != null ? option.getConfig().getRuleKey() : null)
                .configName(option.getConfig() != null ? option.getConfig().getName() : null)
                .optionCode(option.getOptionCode())
                .optionLabel(option.getOptionLabel())
                .modifierType(option.resolvedModifierType())
                .basePrice(option.getBasePrice())
                .build();
    }

    private List<Long> extractSortedOptionIds(Set<CommissionPriceOptionEntity> optionSet, boolean activeOnly) {
        if (optionSet == null || optionSet.isEmpty()) {
            return List.of();
        }
        return optionSet.stream()
                .filter(Objects::nonNull)
                .filter(opt -> !activeOnly || opt.isActive())
                .sorted(optionOrdering())
                .map(CommissionPriceOptionEntity::getOptionId)
                .filter(Objects::nonNull)
                .toList();
    }

    private Comparator<CommissionPriceOptionEntity> optionOrdering() {
        return Comparator.comparing((CommissionPriceOptionEntity o) -> o.getConfig() != null ? o.getConfig().getDisplayOrder() : 0)
                .thenComparing(CommissionPriceOptionEntity::getDisplayOrder)
                .thenComparing(o -> o.getOptionId() != null ? o.getOptionId() : 0L);
    }
}
