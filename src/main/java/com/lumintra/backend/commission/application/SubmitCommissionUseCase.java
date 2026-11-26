package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commerce.domain.service.CustomerSubscriptionBonusLookupService;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.CommissionAttachmentItemRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.SelectedOptionItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.application.service.StaffAutoAssignmentService;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot.OptionSnapshot;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot.RuleSelectionSnapshot;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator.ModifierCalculationResult;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator.ModifierLineInput;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionAttachmentRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.media.domain.model.MediaPurpose;
import com.lumintra.backend.media.domain.service.PrivateMediaReferenceValidator;
import com.lumintra.backend.shared.exception.commission.CommissionRuleNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionTierNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionRuleSelectionException;
import com.lumintra.backend.shared.exception.commission.PresetSelectionMismatchException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UseCase implementing CMS-03: Submitting custom artwork commission requests
 * using unified rule primitives, 3-modifier deterministic math, and preset triggers.
 */
@Service
@RequiredArgsConstructor
public class SubmitCommissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitCommissionUseCase.class);

    private final CommissionPricingTierRepository tierRepository;
    private final CommissionPriceConfigRepository configRepository;
    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionAttachmentRepository attachmentRepository;
    private final StaffAutoAssignmentService staffAutoAssignmentService;
    private final PrivateMediaReferenceValidator privateMediaReferenceValidator;
    private final PricingInvariantValidator pricingInvariantValidator;
    private final CommissionRequestMapper commissionRequestMapper;
    private final ObjectMapper objectMapper;
    private final ApproveCommissionRequestUseCase commissionApproval;
    private final CustomerSubscriptionBonusLookupService subscriptionBonusLookupService;

    @Transactional
    public CustomerCommissionDetailResponse execute(Long customerId, SubmitCommissionRequest request) {
        log.info("Processing commission submission [customerId={}] [tierCode={}] [title={}]",
                customerId, request.tierCode(), request.title());

        List<String> attachmentObjectKeys = validateAttachments(customerId, request.attachments());

        CommissionPricingTierEntity tier = tierRepository.findActiveWithIncludedOptionsByTierCode(request.tierCode())
                .orElseThrow(() -> new CommissionTierNotFoundException("Pricing tier not found or inactive: " + request.tierCode()));

        List<CommissionPriceConfigEntity> activeConfigs = configRepository.findActiveWithActiveOptions();
        Map<Long, CommissionPriceConfigEntity> configsById = activeConfigs.stream()
                .collect(Collectors.toMap(CommissionPriceConfigEntity::getConfigId, Function.identity()));
        Map<String, CommissionPriceConfigEntity> configsByRuleKey = activeConfigs.stream()
                .collect(Collectors.toMap(CommissionPriceConfigEntity::getRuleKey, Function.identity(), (a, b) -> a));

        Map<Long, CommissionPriceOptionEntity> optionsById = new HashMap<>();
        Map<Long, List<CommissionPriceOptionEntity>> activeOptionsByConfigId = new HashMap<>();
        for (CommissionPriceConfigEntity config : activeConfigs) {
            List<CommissionPriceOptionEntity> activeOpts = config.getOptions() == null
                    ? List.of()
                    : config.getOptions().stream().filter(CommissionPriceOptionEntity::isActive).toList();
            activeOptionsByConfigId.put(config.getConfigId(), activeOpts);
            for (CommissionPriceOptionEntity opt : activeOpts) {
                optionsById.put(opt.getOptionId(), opt);
            }
        }

        Map<Long, NormalizedSelection> selectionsByConfigId = normalizeSelections(
                request.selectedOptions(), configsById, activeOptionsByConfigId, optionsById
        );

        Set<Long> allSelectedOptionIds = selectionsByConfigId.values().stream()
                .flatMap(sel -> sel.options().stream())
                .map(CommissionPriceOptionEntity::getOptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> bundledOptionIds = extractActiveOptionIds(tier.getIncludedOptions());
        if (!tier.isCustom()) {
            validatePresetCompatibility(tier, selectionsByConfigId, allSelectedOptionIds, configsById);
        }

        validateDependenciesAndRequiredRules(
                activeConfigs, configsById, optionsById, selectionsByConfigId, allSelectedOptionIds
        );

        String resolvedTargetMatrixDimensions = resolveTargetMatrixDimensions(
                configsByRuleKey, selectionsByConfigId
        );

        // tier.getBasePrice() already represents the final preset package base price (not reduced again by discountPercent)
        CommissionPriceBreakdownSnapshot snapshot = calculateAndBuildSnapshot(
                tier, selectionsByConfigId, bundledOptionIds
        );
        BigDecimal initialPrice = snapshot.computedInitialPrice();
        String priceBreakdownJson = serializeSnapshot(customerId, snapshot);

        Long assignedStaffId = staffAutoAssignmentService.assignNextAvailableStaff();

        int subscriptionMaxRevisions =
                subscriptionBonusLookupService != null
                        ? Math.max(0, subscriptionBonusLookupService.findMaxFreeCommissionRevisions(customerId))
                        : 0;
        int effectiveMaxRevisions = Math.max(tier.getMaxRevisions(), subscriptionMaxRevisions);

        CommissionRequestEntity commission = CommissionRequestEntity.createPendingReview(
                customerId,
                request.title(),
                request.description(),
                tier.getTierCode(),
                resolvedTargetMatrixDimensions,
                priceBreakdownJson,
                initialPrice,
                effectiveMaxRevisions,
                assignedStaffId
        );
        commission = commissionRequestRepository.save(commission);

        List<CommissionAttachmentEntity> savedAttachments = persistAttachments(
                commission.getCommissionId(), request.attachments(), attachmentObjectKeys
        );
        if (Boolean.TRUE.equals(request.openPaymentImmediately())) {
            if (initialPrice.signum() == 0) {
                commission.setStatus(CommissionStatus.PAID);
            } else {
                commissionApproval.openPayment(commission, initialPrice, Instant.now());
            }
        }

        log.info("Commission request created successfully [commissionId={}] [customerId={}] [status={}] [assignedStaffId={}]",
                commission.getCommissionId(), customerId, commission.getStatus(), assignedStaffId);

        return commissionRequestMapper.toCustomerDetailResponse(commission, savedAttachments, List.of());
    }

    /**
     * Each attachment must be a COMMISSION_REFERENCE upload made by this customer; returns the object keys to persist.
     */
    private List<String> validateAttachments(Long customerId, List<CommissionAttachmentItemRequest> attachments) {
        if (attachments == null) {
            return List.of();
        }
        List<String> objectKeys = new ArrayList<>(attachments.size());
        for (CommissionAttachmentItemRequest attachment : attachments) {
            objectKeys.add(privateMediaReferenceValidator.requireOwnedObjectKey(
                    attachment.imageUrl(), MediaPurpose.COMMISSION_REFERENCE, customerId));
        }
        return objectKeys;
    }

    private Map<Long, NormalizedSelection> normalizeSelections(
            List<SelectedOptionItem> items,
            Map<Long, CommissionPriceConfigEntity> configsById,
            Map<Long, List<CommissionPriceOptionEntity>> activeOptionsByConfigId,
            Map<Long, CommissionPriceOptionEntity> optionsById) {

        Map<Long, NormalizedSelection> selections = new LinkedHashMap<>();
        if (items == null) {
            return selections;
        }

        Set<Long> seenConfigs = new HashSet<>();
        for (SelectedOptionItem item : items) {
            CommissionPriceConfigEntity config = configsById.get(item.configId());
            if (config == null) {
                throw new CommissionRuleNotFoundException("Pricing rule config not found: " + item.configId());
            }
            if (!seenConfigs.add(config.getConfigId())) {
                throw new InvalidCommissionRuleSelectionException(
                        "Duplicate selection for rule: " + config.getRuleKey()
                );
            }

            List<CommissionPriceOptionEntity> submittedOptions = resolveAndValidateRuleOptions(
                    config, item.optionIds(), optionsById
            );

            NormalizedSelection normalized = switch (config.getSelectionType()) {
                case SINGLE_SELECT -> normalizeSingleSelect(config, item, submittedOptions);
                case MULTI_SELECT -> normalizeMultiSelect(config, item, submittedOptions);
                case QUANTITY -> normalizeQuantity(config, item, submittedOptions, activeOptionsByConfigId.get(config.getConfigId()));
                case TEXT_INPUT -> normalizeTextInput(config, item, submittedOptions);
            };

            if (normalized != null) {
                selections.put(config.getConfigId(), normalized);
            }
        }
        return selections;
    }

    private List<CommissionPriceOptionEntity> resolveAndValidateRuleOptions(
            CommissionPriceConfigEntity config,
            List<Long> optionIds,
            Map<Long, CommissionPriceOptionEntity> optionsById) {
        if (optionIds == null || optionIds.isEmpty()) {
            return List.of();
        }

        List<CommissionPriceOptionEntity> resolved = new ArrayList<>();
        Set<Long> seenOptionIds = new HashSet<>();
        for (Long optId : optionIds) {
            if (optId == null) {
                throw new InvalidCommissionRuleSelectionException(
                        "Option ID cannot be null for rule: " + config.getRuleKey()
                );
            }
            if (!seenOptionIds.add(optId)) {
                throw new InvalidCommissionRuleSelectionException(
                        "Duplicate option ID " + optId + " in rule: " + config.getRuleKey()
                );
            }
            CommissionPriceOptionEntity opt = optionsById.get(optId);
            if (opt == null) {
                throw new CommissionRuleNotFoundException("Pricing option not found: " + optId);
            }
            if (opt.getConfig() == null || !Objects.equals(opt.getConfig().getConfigId(), config.getConfigId())) {
                throw new InvalidCommissionRuleSelectionException(
                        "Option ID " + optId + " does not belong to rule " + config.getRuleKey()
                );
            }
            resolved.add(opt);
        }
        return resolved;
    }

    private NormalizedSelection normalizeSingleSelect(
            CommissionPriceConfigEntity config,
            SelectedOptionItem item,
            List<CommissionPriceOptionEntity> submittedOptions) {
        if (item.quantity() != null || item.textValue() != null) {
            throw new InvalidCommissionRuleSelectionException(
                    "Rule '" + config.getRuleKey() + "' does not accept quantity or text input"
            );
        }
        if (submittedOptions.size() != 1) {
            throw new InvalidCommissionRuleSelectionException(
                    "Rule '" + config.getRuleKey() + "' requires exactly one option selection"
            );
        }
        return new NormalizedSelection(config, submittedOptions, 1, null);
    }

    private NormalizedSelection normalizeMultiSelect(
            CommissionPriceConfigEntity config,
            SelectedOptionItem item,
            List<CommissionPriceOptionEntity> submittedOptions) {
        if (item.quantity() != null || item.textValue() != null) {
            throw new InvalidCommissionRuleSelectionException(
                    "Rule '" + config.getRuleKey() + "' does not accept quantity or text input"
            );
        }
        if (submittedOptions.isEmpty()) {
            return null;
        }
        return new NormalizedSelection(config, submittedOptions, 1, null);
    }

    private NormalizedSelection normalizeQuantity(
            CommissionPriceConfigEntity config,
            SelectedOptionItem item,
            List<CommissionPriceOptionEntity> submittedOptions,
            List<CommissionPriceOptionEntity> activeRuleOptions) {
        if (item.textValue() != null) {
            throw new InvalidCommissionRuleSelectionException(
                    "QUANTITY rule '" + config.getRuleKey() + "' does not accept text input"
            );
        }
        if (item.quantity() == null) {
            throw new InvalidCommissionRuleSelectionException(
                    "QUANTITY rule '" + config.getRuleKey() + "' requires a quantity value"
            );
        }

        int minQ = config.getMinQuantity() != null ? config.getMinQuantity() : 0;
        int maxQ = config.getMaxQuantity() != null ? config.getMaxQuantity() : Integer.MAX_VALUE;
        int qty = item.quantity();
        if (qty < minQ || qty > maxQ) {
            throw new InvalidCommissionRuleSelectionException(
                    String.format("Quantity for rule '%s' must be between %d and %d", config.getRuleKey(), minQ, maxQ)
            );
        }

        CommissionPriceOptionEntity unitOption;
        if (submittedOptions.isEmpty()) {
            if (activeRuleOptions == null || activeRuleOptions.size() != 1) {
                throw new InvalidCommissionRuleSelectionException(
                        "QUANTITY rule '" + config.getRuleKey() + "' is missing its active unit rate option"
                );
            }
            unitOption = activeRuleOptions.get(0);
        } else if (submittedOptions.size() == 1) {
            unitOption = submittedOptions.get(0);
        } else {
            throw new InvalidCommissionRuleSelectionException(
                    "QUANTITY rule '" + config.getRuleKey() + "' only accepts a single unit rate option"
            );
        }

        return new NormalizedSelection(config, List.of(unitOption), qty, null);
    }

    private NormalizedSelection normalizeTextInput(
            CommissionPriceConfigEntity config,
            SelectedOptionItem item,
            List<CommissionPriceOptionEntity> submittedOptions) {
        if (item.quantity() != null) {
            throw new InvalidCommissionRuleSelectionException(
                    "TEXT_INPUT rule '" + config.getRuleKey() + "' does not accept quantity input"
            );
        }
        if (item.textValue() == null && submittedOptions.isEmpty()) {
            return null;
        }
        return new NormalizedSelection(config, submittedOptions, 1, item.textValue());
    }

    private void validatePresetCompatibility(
            CommissionPricingTierEntity tier,
            Map<Long, NormalizedSelection> selectionsByConfigId,
            Set<Long> allSelectedOptionIds,
            Map<Long, CommissionPriceConfigEntity> configsById) {

        Set<Long> requiredTriggerIds = extractActiveOptionIds(tier.getTriggerOptions());
        if (!allSelectedOptionIds.containsAll(requiredTriggerIds)) {
            throw new PresetSelectionMismatchException(
                    "Selected options do not satisfy trigger requirements for preset tier " + tier.getTierCode()
            );
        }

        if (tier.getIncludedOptions() == null || tier.getIncludedOptions().isEmpty()) {
            return;
        }

        Map<Long, Set<Long>> bundledByConfigId = new HashMap<>();
        for (CommissionPriceOptionEntity bundledOpt : tier.getIncludedOptions()) {
            if (bundledOpt != null && bundledOpt.isActive() && bundledOpt.getConfig() != null
                    && bundledOpt.getOptionId() != null) {
                bundledByConfigId.computeIfAbsent(bundledOpt.getConfig().getConfigId(), k -> new HashSet<>())
                        .add(bundledOpt.getOptionId());
            }
        }

        for (Map.Entry<Long, Set<Long>> entry : bundledByConfigId.entrySet()) {
            Long configId = entry.getKey();
            Set<Long> expectedOptionIds = entry.getValue();
            NormalizedSelection selected = selectionsByConfigId.get(configId);
            Set<Long> selectedIds = selected == null
                    ? Set.of()
                    : selected.options().stream()
                            .map(CommissionPriceOptionEntity::getOptionId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

            CommissionPriceConfigEntity config = configsById.get(configId);
            boolean matches = config != null && config.getSelectionType() == SelectionType.SINGLE_SELECT
                    ? selectedIds.equals(expectedOptionIds)
                    : selectedIds.containsAll(expectedOptionIds);

            if (!matches) {
                throw new PresetSelectionMismatchException(
                        "Submitted selections do not match preset tier requirements for " + tier.getTierCode()
                );
            }
        }
    }

    private void validateDependenciesAndRequiredRules(
            List<CommissionPriceConfigEntity> activeConfigs,
            Map<Long, CommissionPriceConfigEntity> configsById,
            Map<Long, CommissionPriceOptionEntity> optionsById,
            Map<Long, NormalizedSelection> selectionsByConfigId,
            Set<Long> allSelectedOptionIds) {

        for (CommissionPriceConfigEntity config : activeConfigs) {
            boolean hasSelection = selectionsByConfigId.containsKey(config.getConfigId());
            boolean isEligible = isRuleEligible(
                    config, configsById, optionsById, allSelectedOptionIds, 0, new HashSet<>()
            );

            if (!isEligible && hasSelection) {
                throw new InvalidCommissionRuleSelectionException(
                        "Rule '" + config.getRuleKey() + "' is not eligible because its prerequisite option chain was not selected"
                );
            }

            if (isEligible && config.isRequired() && !hasSelection) {
                throw new InvalidCommissionRuleSelectionException(
                        "Required rule '" + config.getRuleKey() + "' must have a selection"
                );
            }
        }
    }

    private boolean isRuleEligible(
            CommissionPriceConfigEntity config,
            Map<Long, CommissionPriceConfigEntity> configsById,
            Map<Long, CommissionPriceOptionEntity> optionsById,
            Set<Long> allSelectedOptionIds,
            int depth,
            Set<Long> visitedConfigIds) {

        if (depth > PricingInvariantValidator.MAX_DEPENDENCY_DEPTH) {
            throw new InvalidCommissionRuleSelectionException(
                    "Rule dependency chain exceeds maximum allowed depth of " + PricingInvariantValidator.MAX_DEPENDENCY_DEPTH
            );
        }

        if (config.getConfigId() != null && !visitedConfigIds.add(config.getConfigId())) {
            throw new InvalidCommissionRuleSelectionException("Circular conditional dependency detected in active rules");
        }

        Long parentOptionId = config.getDependsOnOptionId();
        if (parentOptionId == null) {
            return true;
        }

        if (!allSelectedOptionIds.contains(parentOptionId)) {
            return false;
        }

        CommissionPriceOptionEntity parentOption = optionsById.get(parentOptionId);
        if (parentOption == null || parentOption.getConfig() == null) {
            return false;
        }

        CommissionPriceConfigEntity parentConfig = configsById.get(parentOption.getConfig().getConfigId());
        if (parentConfig == null) {
            return false;
        }

        return isRuleEligible(parentConfig, configsById, optionsById, allSelectedOptionIds, depth + 1, visitedConfigIds);
    }

    private String resolveTargetMatrixDimensions(
            Map<String, CommissionPriceConfigEntity> configsByRuleKey,
            Map<Long, NormalizedSelection> selectionsByConfigId) {
        CommissionPriceConfigEntity matrixConfig = configsByRuleKey.get("MATRIX_SIZE");
        if (matrixConfig == null) {
            return null;
        }

        NormalizedSelection matrixSelection = selectionsByConfigId.get(matrixConfig.getConfigId());
        if (matrixSelection == null || matrixSelection.options().isEmpty()) {
            return null;
        }

        return matrixSelection.options().get(0).getOptionCode();
    }

    private CommissionPriceBreakdownSnapshot calculateAndBuildSnapshot(
            CommissionPricingTierEntity tier,
            Map<Long, NormalizedSelection> selectionsByConfigId,
            Set<Long> bundledOptionIds) {

        List<ModifierLineInput> chargeLines = new ArrayList<>();
        Map<LineKey, Integer> chargeIndexByKey = new HashMap<>();

        for (NormalizedSelection selection : selectionsByConfigId.values()) {
            for (CommissionPriceOptionEntity option : selection.options()) {
                boolean included = !tier.isCustom() && bundledOptionIds.contains(option.getOptionId());
                if (!included) {
                    int idx = chargeLines.size();
                    chargeLines.add(new ModifierLineInput(selection.config(), option, selection.quantity()));
                    chargeIndexByKey.put(new LineKey(selection.config().getConfigId(), option.getOptionId()), idx);
                }
            }
        }

        BigDecimal tierBase = tier.getBasePrice() != null ? tier.getBasePrice() : BigDecimal.ZERO;
        ModifierCalculationResult calc = pricingInvariantValidator.calculateModifierPipeline(tierBase, chargeLines);

        List<RuleSelectionSnapshot> ruleSnapshots = new ArrayList<>();
        for (NormalizedSelection selection : selectionsByConfigId.values()) {
            List<OptionSnapshot> optionSnapshots = new ArrayList<>();
            BigDecimal ruleLineAmount = BigDecimal.ZERO;

            for (CommissionPriceOptionEntity option : selection.options()) {
                boolean included = !tier.isCustom() && bundledOptionIds.contains(option.getOptionId());
                BigDecimal amount;
                if (included) {
                    amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                } else {
                    Integer idx = chargeIndexByKey.get(new LineKey(selection.config().getConfigId(), option.getOptionId()));
                    amount = idx != null && idx < calc.lineAmounts().size()
                            ? calc.lineAmounts().get(idx)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }

                ruleLineAmount = ruleLineAmount.add(amount);
                optionSnapshots.add(new OptionSnapshot(
                        option.getOptionId(),
                        option.getOptionCode(),
                        option.getOptionLabel(),
                        option.resolvedModifierType(),
                        option.getBasePrice() != null ? option.getBasePrice() : BigDecimal.ZERO,
                        included,
                        amount
                ));
            }

            ruleSnapshots.add(new RuleSelectionSnapshot(
                    selection.config().getConfigId(),
                    selection.config().getRuleKey(),
                    selection.config().getName(),
                    selection.config().getSelectionType(),
                    selection.config().isBasePrice(),
                    selection.config().getSelectionType() == SelectionType.QUANTITY ? selection.quantity() : null,
                    selection.textValue(),
                    optionSnapshots,
                    ruleLineAmount.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        BigDecimal initialPrice = calc.finalTotal();
        return new CommissionPriceBreakdownSnapshot(
                tier.getTierCode(),
                tier.getName(),
                tier.isCustom(),
                tierBase.setScale(2, RoundingMode.HALF_UP),
                tier.getDiscountPercent(),
                tier.getMaxRevisions(),
                ruleSnapshots,
                calc.baseBucketTotal(),
                initialPrice,
                initialPrice
        );
    }

    private Set<Long> extractActiveOptionIds(Set<CommissionPriceOptionEntity> optionSet) {
        if (optionSet == null || optionSet.isEmpty()) {
            return Collections.emptySet();
        }
        return optionSet.stream()
                .filter(Objects::nonNull)
                .filter(CommissionPriceOptionEntity::isActive)
                .map(CommissionPriceOptionEntity::getOptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String serializeSnapshot(Long customerId, CommissionPriceBreakdownSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize price breakdown snapshot [customerId={}]", customerId, e);
            return "{}";
        }
    }

    private List<CommissionAttachmentEntity> persistAttachments(
            Long commissionId,
            List<CommissionAttachmentItemRequest> attachments,
            List<String> attachmentObjectKeys) {
        List<CommissionAttachmentEntity> savedAttachments = new ArrayList<>();
        if (attachments != null && !attachments.isEmpty()) {
            for (int i = 0; i < attachments.size(); i++) {
                CommissionAttachmentItemRequest attReq = attachments.get(i);
                CommissionAttachmentEntity attachment = CommissionAttachmentEntity.create(
                        commissionId,
                        attachmentObjectKeys.get(i),
                        attReq.caption(),
                        i
                );
                savedAttachments.add(attachmentRepository.save(attachment));
            }
        }
        return savedAttachments;
    }

    private record NormalizedSelection(
            CommissionPriceConfigEntity config,
            List<CommissionPriceOptionEntity> options,
            int quantity,
            String textValue
    ) {}

    private record LineKey(Long configId, Long optionId) {}
}
