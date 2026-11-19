package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UpdatePricingRuleRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.SystemPricingRules;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.shared.exception.commission.CommissionRuleNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import com.lumintra.backend.shared.exception.commission.PricingRuleInUseException;
import com.lumintra.backend.shared.exception.commission.SystemRuleLockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Partial update (PATCH) and soft deletion of commission pricing rules.
 *
 * <p>System rules: name, description, display order and option label / price / modifier / active flag /
 * display order are editable; the rule code, selection type, UI type, base flag, quantity bounds, required
 * flag, dependency and the rule's own active flag are locked (409), and seeded core options can't be removed.
 *
 * <p>Custom rules: everything except the rule code and selection type is editable, including a multi-level
 * acyclic dependency (max depth {@value PricingInvariantValidator#MAX_DEPENDENCY_DEPTH}).
 *
 * <p>Options are never hard-deleted. Deactivating an option or rule that an active preset or another
 * active rule still uses is rejected with 409. Prices can change freely: no CUSTOM-over-preset floor.
 */
@Service
@RequiredArgsConstructor
public class UpdatePricingRuleUseCase {
    private final CommissionPriceConfigRepository configRepository;
    private final CommissionPriceOptionRepository optionRepository;
    private final CommissionPricingTierRepository tierRepository;
    private final PricingInvariantValidator invariantValidator;
    private final GetManagementPricingRuleDetailUseCase getRuleDetail;

    @Transactional
    public ManagementPricingRuleResponse execute(Long ruleId, UpdatePricingRuleRequest request) {
        CommissionPriceConfigEntity rule = findRule(ruleId);
        boolean reactivating = Boolean.TRUE.equals(request.isActive()) && !rule.isActive();
        if (!rule.isActive() && !reactivating) {
            throw new InvalidRuleConfigurationException("Tiêu chí này đã bị ẩn.");
        }

        rejectImmutableChanges(rule, request);
        if (rule.isSystem()) {
            rejectSystemLockedChanges(rule, request);
        }
        applyRuleFields(rule, request);

        boolean appended = applyOptionChanges(rule, request);

        if (!rule.isSystem()) {
            changeDependency(rule, request);
        }
        if (reactivating) {
            requireActiveParent(rule);
            rule.setActive(true);
        }
        if (Boolean.FALSE.equals(request.isActive()) && rule.isActive()) {
            deactivateRule(rule);
        }

        if (rule.getSelectionType() != SelectionType.TEXT_INPUT
                && rule.getOptions().stream().noneMatch(CommissionPriceOptionEntity::isActive)) {
            throw new InvalidRuleConfigurationException("Tiêu chí phải còn ít nhất một phương án.");
        }
        if (rule.isSystem()) {
            invariantValidator.validateRule(rule);
        } else {
            invariantValidator.validateCustomRuleCreation(rule);
        }
        if (appended) {
            configRepository.flush();
        }
        return getRuleDetail.execute(ruleId);
    }

    @Transactional
    public void delete(Long ruleId) {
        CommissionPriceConfigEntity rule = findRule(ruleId);
        if (!rule.isActive()) return;
        if (rule.isSystem()) {
            throw new SystemRuleLockedException("Không thể xóa tiêu chí hệ thống.");
        }
        deactivateRule(rule);
    }

    private CommissionPriceConfigEntity findRule(Long ruleId) {
        return configRepository.findById(ruleId)
                .orElseThrow(() -> new CommissionRuleNotFoundException(
                        "Commission pricing rule with ID " + ruleId + " not found."));
    }

    private void rejectImmutableChanges(CommissionPriceConfigEntity rule, UpdatePricingRuleRequest request) {
        boolean codeChanged = request.configCode() != null
                && !request.configCode().trim().equalsIgnoreCase(rule.getRuleKey());
        boolean typeChanged = request.selectionType() != null && request.selectionType() != rule.getSelectionType();
        if (!codeChanged && !typeChanged) {
            return;
        }
        String message = "configCode and selectionType cannot be changed.";
        if (rule.isSystem()) {
            throw new SystemRuleLockedException(message);
        }
        throw new InvalidRuleConfigurationException(message);
    }

    private void rejectSystemLockedChanges(CommissionPriceConfigEntity rule, UpdatePricingRuleRequest request) {
        if (Boolean.FALSE.equals(request.isActive())) {
            throw new SystemRuleLockedException("System pricing rules cannot be deactivated.");
        }
        boolean locked = (request.uiType() != null && request.uiType() != rule.getUiType())
                || (request.isBasePrice() != null && request.isBasePrice() != rule.isBasePrice())
                || (request.minQuantity() != null && !request.minQuantity().equals(rule.getMinQuantity()))
                || (request.maxQuantity() != null && !request.maxQuantity().equals(rule.getMaxQuantity()))
                || (request.isRequired() != null && request.isRequired() != rule.isRequired())
                || (request.dependsOnOptionId() != null
                        && !request.dependsOnOptionId().equals(rule.getDependsOnOptionId()))
                || (Boolean.TRUE.equals(request.clearDependsOnOption()) && rule.getDependsOnOptionId() != null);
        if (locked) {
            throw new SystemRuleLockedException(
                    "System pricing rules only allow editing the name, description, display order and options.");
        }
    }

    private void applyRuleFields(CommissionPriceConfigEntity rule, UpdatePricingRuleRequest request) {
        if (request.configName() != null) {
            rule.setName(requireText(request.configName(), "configName"));
        }
        if (request.description() != null) {
            String description = request.description().trim();
            rule.setDescription(description.isEmpty() ? null : description);
        }
        if (request.displayOrder() != null) {
            rule.setDisplayOrder(request.displayOrder());
        }
        if (rule.isSystem()) {
            return;
        }
        if (request.uiType() != null) {
            rule.setUiType(request.uiType());
        }
        if (request.isBasePrice() != null) {
            rule.setBasePrice(request.isBasePrice());
        }
        if (request.minQuantity() != null) {
            rule.setMinQuantity(request.minQuantity());
        }
        if (request.maxQuantity() != null) {
            rule.setMaxQuantity(request.maxQuantity());
        }
        if (request.isRequired() != null) {
            rule.setRequired(request.isRequired());
        }
    }

    /** @return whether options were appended */
    private boolean applyOptionChanges(CommissionPriceConfigEntity rule, UpdatePricingRuleRequest request) {
        Map<Long, CommissionPriceOptionEntity> optionsById = new HashMap<>();
        for (CommissionPriceOptionEntity option : rule.getOptions()) {
            optionsById.put(option.getOptionId(), option);
        }

        Set<Long> updated = new HashSet<>();
        Set<Long> toDeactivate = new LinkedHashSet<>();
        for (UpdatePricingRuleRequest.OptionUpdate update : nullToEmpty(request.options())) {
            CommissionPriceOptionEntity option = optionsById.get(update.optionId());
            if (option == null || !updated.add(update.optionId())) {
                throw new InvalidRuleConfigurationException("Invalid or duplicate option ID: " + update.optionId());
            }
            if (update.optionLabel() != null) {
                option.setOptionLabel(requireText(update.optionLabel(), "optionLabel"));
            }
            if (update.basePrice() != null) {
                option.setBasePrice(update.basePrice());
            }
            if (update.modifierType() != null) {
                option.setModifierType(update.modifierType());
            }
            if (update.displayOrder() != null) {
                option.setDisplayOrder(update.displayOrder());
            }
            if (Boolean.FALSE.equals(update.isActive())) {
                toDeactivate.add(option.getOptionId());
            } else if (Boolean.TRUE.equals(update.isActive())) {
                option.setActive(true);
            }
        }

        List<Long> removed = nullToEmpty(request.removedOptionIds());
        Set<Long> uniqueRemoved = new HashSet<>(removed);
        if (uniqueRemoved.size() != removed.size()
                || !optionsById.keySet().containsAll(uniqueRemoved)
                || uniqueRemoved.stream().anyMatch(updated::contains)) {
            throw new InvalidRuleConfigurationException(
                    "Phương án xóa bị trùng, không thuộc tiêu chí hoặc đang được cập nhật.");
        }
        toDeactivate.addAll(removed);

        List<UpdatePricingRuleRequest.NewOption> newOptions = nullToEmpty(request.newOptions());
        if (!newOptions.isEmpty()) {
            appendOptions(rule, newOptions);
        }
        if (!toDeactivate.isEmpty()) {
            deactivateOptions(rule, toDeactivate.stream().map(optionsById::get).toList());
        }
        return !newOptions.isEmpty();
    }

    private void deactivateOptions(CommissionPriceConfigEntity rule, List<CommissionPriceOptionEntity> options) {
        if (rule.getSelectionType() == SelectionType.QUANTITY) {
            throw new InvalidRuleConfigurationException("Không thể xóa phương án của tiêu chí này.");
        }
        if (rule.isSystem()) {
            List<String> core = options.stream()
                    .map(CommissionPriceOptionEntity::getOptionCode)
                    .filter(code -> SystemPricingRules.isCoreOption(rule.getRuleKey(), code))
                    .toList();
            if (!core.isEmpty()) {
                throw new SystemRuleLockedException("Core system options cannot be removed: " + String.join(", ", core));
            }
        }
        ensureOptionsUnused(options.stream().map(CommissionPriceOptionEntity::getOptionId).collect(Collectors.toSet()));
        options.forEach(option -> option.setActive(false));
    }

    private void deactivateRule(CommissionPriceConfigEntity rule) {
        ensureOptionsUnused(rule.getOptions().stream()
                .map(CommissionPriceOptionEntity::getOptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        rule.setActive(false);
    }

    private void ensureOptionsUnused(Set<Long> optionIds) {
        if (optionIds.isEmpty()) return;
        if (configRepository.existsByIsActiveTrueAndDependsOnOptionIdIn(optionIds)) {
            throw new PricingRuleInUseException("Phương án đang được tiêu chí khác sử dụng.");
        }
        for (CommissionPricingTierEntity tier : tierRepository.findActiveWithIncludedOptions()) {
            if (references(tier, optionIds)) {
                throw new PricingRuleInUseException("Phương án đang được gói thiết kế sử dụng.");
            }
        }
    }

    private static boolean references(CommissionPricingTierEntity tier, Set<Long> optionIds) {
        return nullToEmpty(tier.getIncludedOptions()).stream().anyMatch(o -> optionIds.contains(o.getOptionId()))
                || nullToEmpty(tier.getTriggerOptions()).stream().anyMatch(o -> optionIds.contains(o.getOptionId()));
    }

    private void changeDependency(CommissionPriceConfigEntity rule, UpdatePricingRuleRequest request) {
        boolean clear = Boolean.TRUE.equals(request.clearDependsOnOption());
        Long parentId = request.dependsOnOptionId();
        if (clear && parentId != null) {
            throw new InvalidRuleConfigurationException(
                    "dependsOnOptionId and clearDependsOnOption cannot be used together.");
        }
        if (clear) {
            rule.setDependsOnOptionId(null);
            return;
        }
        if (parentId == null || parentId.equals(rule.getDependsOnOptionId())) {
            return;
        }
        CommissionPriceOptionEntity parent = findActiveParent(parentId);
        invariantValidator.validateParentOptionDependency(
                rule.getConfigId(), parent, id -> optionRepository.findById(id).orElse(null),
                countDescendantLevels(rule));
        rule.setDependsOnOptionId(parentId);
        ensurePresetsStillConsistent(rule);
    }

    private void requireActiveParent(CommissionPriceConfigEntity rule) {
        if (rule.getDependsOnOptionId() != null) {
            findActiveParent(rule.getDependsOnOptionId());
        }
    }

    private CommissionPriceOptionEntity findActiveParent(Long parentId) {
        CommissionPriceOptionEntity parent = optionRepository.findById(parentId)
                .orElseThrow(() -> new InvalidRuleConfigurationException(
                        String.format("Parent option with ID %d does not exist.", parentId)));
        if (!parent.isActive() || parent.getConfig() == null || !parent.getConfig().isActive()) {
            throw new InvalidRuleConfigurationException(
                    String.format("Parent option with ID %d is not active.", parentId));
        }
        return parent;
    }

    /** Number of rule levels that depend (directly or transitively) on this rule's options. */
    private int countDescendantLevels(CommissionPriceConfigEntity rule) {
        Set<Long> visited = new HashSet<>();
        visited.add(rule.getConfigId());
        Set<Long> frontier = optionIds(rule.getOptions());
        int levels = 0;
        while (!frontier.isEmpty() && levels <= PricingInvariantValidator.MAX_DEPENDENCY_DEPTH) {
            List<CommissionPriceConfigEntity> children = configRepository
                    .findAllByIsActiveTrueAndDependsOnOptionIdIn(frontier).stream()
                    .filter(child -> visited.add(child.getConfigId()))
                    .toList();
            if (children.isEmpty()) {
                break;
            }
            levels++;
            frontier = children.stream()
                    .flatMap(child -> nullToEmpty(child.getOptions()).stream())
                    .map(CommissionPriceOptionEntity::getOptionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }
        return levels;
    }

    /** A preset that bundles this rule's options must also bundle or trigger on the new parent option. */
    private void ensurePresetsStillConsistent(CommissionPriceConfigEntity rule) {
        Set<Long> ruleOptionIds = optionIds(rule.getOptions());
        for (CommissionPricingTierEntity tier : tierRepository.findActiveWithIncludedOptions()) {
            if (tier.isCustom() || !references(tier, ruleOptionIds)) {
                continue;
            }
            try {
                invariantValidator.validateTier(tier, null);
            } catch (InvalidRuleConfigurationException ex) {
                throw new PricingRuleInUseException(ex.getMessage());
            }
        }
    }

    private void appendOptions(CommissionPriceConfigEntity rule, List<UpdatePricingRuleRequest.NewOption> additions) {
        if (SystemPricingRules.FORMAT_TYPE.equals(rule.getRuleKey())
                || (rule.getSelectionType() != SelectionType.SINGLE_SELECT && rule.getSelectionType() != SelectionType.MULTI_SELECT)) {
            throw new InvalidRuleConfigurationException("This pricing rule does not support additional options.");
        }

        Set<String> optionCodes = new HashSet<>();
        int displayOrder = 0;
        for (CommissionPriceOptionEntity option : rule.getOptions()) {
            optionCodes.add(option.getOptionCode().toUpperCase(Locale.ROOT));
            displayOrder = Math.max(displayOrder, option.getDisplayOrder());
        }
        for (UpdatePricingRuleRequest.NewOption addition : additions) {
            String code = "OPTION_" + UUID.randomUUID();
            if (SystemPricingRules.MATRIX_SIZE.equals(rule.getRuleKey())) {
                String dimensions = addition.matrixDimensions();
                if (dimensions == null || !dimensions.trim().matches("[1-9][0-9]{0,3}[xX][1-9][0-9]{0,3}")) {
                    throw new InvalidRuleConfigurationException("A matrix option requires dimensions such as 32x64.");
                }
                code = dimensions.trim().toLowerCase(Locale.ROOT);
            }
            if (!optionCodes.add(code.toUpperCase(Locale.ROOT))) {
                throw new InvalidRuleConfigurationException("Option already exists: " + code);
            }
            rule.addOption(CommissionPriceOptionEntity.create(
                    code, addition.optionLabel(), addition.modifierType(), addition.basePrice(), false, ++displayOrder));
        }
    }

    private static Set<Long> optionIds(List<CommissionPriceOptionEntity> options) {
        return nullToEmpty(options).stream()
                .map(CommissionPriceOptionEntity::getOptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static String requireText(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidRuleConfigurationException(field + " cannot be blank.");
        }
        return trimmed;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <T> Set<T> nullToEmpty(Set<T> set) {
        return set == null ? Set.of() : set;
    }
}
