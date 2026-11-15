package com.lumintra.backend.commission.infrastructure.persistence.mapper;

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
        List<Long> bundledOptionIds;

        if (tier.isCustom()) {
            customCalculatedPrice = tier.getBasePrice() != null ? tier.getBasePrice() : BigDecimal.ZERO;
            savingsAmount = BigDecimal.ZERO;
            bundledOptionIds = List.of();
        } else {
            customCalculatedPrice = invariantValidator.calculateCustomCalculatedPrice(tier, customTier);
            savingsAmount = invariantValidator.calculateSavingsAmount(customCalculatedPrice, tier.getBasePrice());

            Set<CommissionPriceOptionEntity> options = tier.getIncludedOptions() != null
                    ? tier.getIncludedOptions()
                    : Collections.emptySet();

            bundledOptionIds = options.stream()
                    .filter(CommissionPriceOptionEntity::isActive)
                    .sorted(Comparator.comparing((CommissionPriceOptionEntity o) -> o.getConfig() != null ? o.getConfig().getDisplayOrder() : 0)
                            .thenComparing(CommissionPriceOptionEntity::getDisplayOrder)
                            .thenComparing(o -> o.getOptionId() != null ? o.getOptionId() : 0L))
                    .map(CommissionPriceOptionEntity::getOptionId)
                    .filter(Objects::nonNull)
                    .toList();
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
                .basePrice(option.getBasePrice())
                .isDefault(option.isDefault())
                .displayOrder(option.getDisplayOrder())
                .build();
    }
}
