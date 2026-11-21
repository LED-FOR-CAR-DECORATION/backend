package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UpdateCommissionPresetsRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates or replaces commission presets and the mandatory CUSTOM base mode (CMS pricing, issue #107).
 *
 * <p>Each listed tier is replaced as a whole, keyed by its upper-cased {@code tierCode}; an unknown code creates
 * a new preset, tiers not listed stay unchanged. Presets are never hard-deleted: {@code isActive = false} hides
 * one. Commissions snapshot their price breakdown at submission, so changes only affect future requests, and
 * no CUSTOM-over-preset price floor is enforced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateCommissionPresetsUseCase {

    static final String CUSTOM_CODE = "CUSTOM";
    private static final BigDecimal DEFAULT_EXTRA_REVISION_FEE_PERCENT = new BigDecimal("10.00");

    private final CommissionPricingTierRepository tierRepository;
    private final CommissionPriceOptionRepository optionRepository;
    private final PricingInvariantValidator invariantValidator;
    private final CommissionPricingMapper pricingMapper;

    @Transactional
    public List<ManagementPricingTierResponse> execute(UpdateCommissionPresetsRequest request) {
        List<UpdateCommissionPresetsRequest.Preset> items = request.presets();
        rejectDuplicateCodes(items);

        Map<String, CommissionPricingTierEntity> tiersByCode = tierRepository.findAll().stream()
                .collect(Collectors.toMap(t -> normalize(t.getTierCode()), Function.identity(), (a, b) -> a));
        Map<Long, CommissionPriceOptionEntity> optionsById = loadOptions(items);

        CommissionPricingTierEntity customTier = tiersByCode.values().stream()
                .filter(CommissionPricingTierEntity::isCustom)
                .findFirst()
                .orElse(null);

        List<CommissionPricingTierEntity> changed = new ArrayList<>();
        for (UpdateCommissionPresetsRequest.Preset item : items) {
            String code = normalize(item.tierCode());
            CommissionPricingTierEntity tier = tiersByCode.get(code);
            boolean customCode = CUSTOM_CODE.equals(code);
            if (customCode && (tier != null ? !tier.isCustom() : customTier != null)) {
                throw new InvalidRuleConfigurationException("Tier code CUSTOM is reserved for the CUSTOM base mode.");
            }
            if (tier == null) {
                tier = newTier(code, customCode);
                tiersByCode.put(code, tier);
                if (customCode) {
                    customTier = tier;
                }
            }
            apply(tier, item, optionsById);
            changed.add(tier);
        }

        for (CommissionPricingTierEntity tier : changed) {
            invariantValidator.validateTier(tier, customTier);
        }
        tierRepository.saveAll(changed);
        log.info("Commission presets updated [tierCodes={}]",
                changed.stream().map(CommissionPricingTierEntity::getTierCode).toList());

        CommissionPricingTierEntity activeCustom = customTier != null && customTier.isActive() ? customTier : null;
        return tiersByCode.values().stream()
                .sorted(Comparator.comparingInt(CommissionPricingTierEntity::getDisplayOrder)
                        .thenComparing(CommissionPricingTierEntity::getTierCode))
                .map(tier -> pricingMapper.toManagementTierDetail(tier, activeCustom))
                .toList();
    }

    private void apply(CommissionPricingTierEntity tier, UpdateCommissionPresetsRequest.Preset item,
                       Map<Long, CommissionPriceOptionEntity> optionsById) {
        boolean active = item.isActive() == null || item.isActive();
        BigDecimal discount = item.discountPercent() != null ? item.discountPercent() : BigDecimal.ZERO;
        Set<CommissionPriceOptionEntity> included = resolve(item.includedOptionIds(), optionsById);
        Set<CommissionPriceOptionEntity> triggers = resolve(item.triggerOptionIds(), optionsById);

        if (tier.isCustom()) {
            if (!active) {
                throw new InvalidRuleConfigurationException("CUSTOM tier cannot be deactivated.");
            }
            if (!included.isEmpty() || !triggers.isEmpty()) {
                throw new InvalidRuleConfigurationException(
                        "CUSTOM tier cannot bundle options or have trigger options.");
            }
            if (discount.signum() != 0) {
                throw new InvalidRuleConfigurationException("CUSTOM tier cannot have a bundle discount.");
            }
        } else {
            validateBundledOptions(tier.getTierCode(), included, active);
            validateTriggerOptions(tier.getTierCode(), triggers, active);
        }

        tier.setName(item.tierName().trim());
        tier.setDescription(item.description() != null ? item.description().trim() : null);
        tier.setBasePrice(item.basePrice());
        tier.setDiscountPercent(discount);
        tier.setMaxRevisions(item.maxRevisions());
        tier.setExtraRevisionFeePercent(item.extraRevisionFeePercent() != null
                ? item.extraRevisionFeePercent() : DEFAULT_EXTRA_REVISION_FEE_PERCENT);
        tier.setActive(active);
        tier.setDisplayOrder(item.displayOrder() != null ? item.displayOrder() : 0);
        tier.getIncludedOptions().clear();
        tier.getIncludedOptions().addAll(included);
        tier.getTriggerOptions().clear();
        tier.getTriggerOptions().addAll(triggers);
    }

    private void validateBundledOptions(String tierCode, Set<CommissionPriceOptionEntity> included, boolean active) {
        Set<Long> singleSelectRules = new HashSet<>();
        for (CommissionPriceOptionEntity option : included) {
            CommissionPriceConfigEntity rule = option.getConfig();
            SelectionType type = rule != null ? rule.getSelectionType() : null;
            if (type != SelectionType.SINGLE_SELECT && type != SelectionType.MULTI_SELECT) {
                throw new InvalidRuleConfigurationException(String.format(
                        "Preset '%s' can only bundle options of SINGLE_SELECT or MULTI_SELECT rules (option ID %d).",
                        tierCode, option.getOptionId()));
            }
            if (type == SelectionType.SINGLE_SELECT && !singleSelectRules.add(rule.getConfigId())) {
                throw new InvalidRuleConfigurationException(String.format(
                        "Preset '%s' bundles more than one option of single-select rule '%s'.",
                        tierCode, rule.getRuleKey()));
            }
            if (active) {
                requireActive(tierCode, option);
            }
        }
    }

    private void validateTriggerOptions(String tierCode, Set<CommissionPriceOptionEntity> triggers, boolean active) {
        if (active) {
            triggers.forEach(option -> requireActive(tierCode, option));
        }
    }

    private void requireActive(String tierCode, CommissionPriceOptionEntity option) {
        if (!option.isActive() || option.getConfig() == null || !option.getConfig().isActive()) {
            throw new InvalidRuleConfigurationException(String.format(
                    "Preset '%s' references inactive option ID %d.", tierCode, option.getOptionId()));
        }
    }

    private Map<Long, CommissionPriceOptionEntity> loadOptions(List<UpdateCommissionPresetsRequest.Preset> items) {
        Set<Long> ids = new HashSet<>();
        for (UpdateCommissionPresetsRequest.Preset item : items) {
            ids.addAll(nullToEmpty(item.includedOptionIds()));
            ids.addAll(nullToEmpty(item.triggerOptionIds()));
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, CommissionPriceOptionEntity> found = new HashMap<>();
        optionRepository.findAllById(ids).forEach(option -> found.put(option.getOptionId(), option));
        ids.stream().filter(id -> !found.containsKey(id)).sorted().findFirst().ifPresent(id -> {
            throw new InvalidRuleConfigurationException(String.format("Option with ID %d does not exist.", id));
        });
        return found;
    }

    private static Set<CommissionPriceOptionEntity> resolve(List<Long> ids, Map<Long, CommissionPriceOptionEntity> optionsById) {
        return nullToEmpty(ids).stream()
                .map(optionsById::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void rejectDuplicateCodes(List<UpdateCommissionPresetsRequest.Preset> items) {
        Set<String> seen = new HashSet<>();
        for (UpdateCommissionPresetsRequest.Preset item : items) {
            if (!seen.add(normalize(item.tierCode()))) {
                throw new InvalidRuleConfigurationException(
                        String.format("Tier code '%s' is listed more than once.", normalize(item.tierCode())));
            }
        }
    }

    private static CommissionPricingTierEntity newTier(String code, boolean custom) {
        return CommissionPricingTierEntity.builder()
                .tierCode(code)
                .isCustom(custom)
                .includedOptions(new HashSet<>())
                .triggerOptions(new HashSet<>())
                .build();
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> Collection<T> nullToEmpty(Collection<T> values) {
        return values == null ? List.of() : values;
    }
}
