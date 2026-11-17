package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.CommissionAttachmentItemRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.SelectedOptionItem;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.Step1SelectionItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.application.service.StaffAutoAssignmentService;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot.OptionSnapshot;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot.Step1SelectionSnapshot;
import com.lumintra.backend.commission.domain.model.CommissionPriceBreakdownSnapshot.Step2SelectionSnapshot;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
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
import com.lumintra.backend.media.domain.service.MediaUrlValidator;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UseCase implementing CMS-03: Submitting custom commission artwork requests.
 * Orchestrates 2-stage pricing validation, rule dependency enforcement, snapshotting,
 * staff auto-assignment, and attachment persistence.
 */
@Service
@RequiredArgsConstructor
public class SubmitCommissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitCommissionUseCase.class);
    private static final int MAX_DEPENDENCY_DEPTH = 5;

    private final CommissionPricingTierRepository tierRepository;
    private final CommissionPriceConfigRepository configRepository;
    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionAttachmentRepository attachmentRepository;
    private final StaffAutoAssignmentService staffAutoAssignmentService;
    private final MediaUrlValidator mediaUrlValidator;
    private final CommissionRequestMapper commissionRequestMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CustomerCommissionDetailResponse execute(Long customerId, SubmitCommissionRequest request) {
        log.info("Processing commission submission [customerId={}] [tierCode={}] [title={}]",
                customerId, request.tierCode(), request.title());

        // 1. Pre-validate media URLs
        if (request.attachments() != null) {
            for (CommissionAttachmentItemRequest attachment : request.attachments()) {
                mediaUrlValidator.validateMediaUrl(attachment.imageUrl());
            }
        }

        // 2. Load active pricing tier with included options
        CommissionPricingTierEntity tier = tierRepository.findActiveWithIncludedOptionsByTierCode(request.tierCode())
                .orElseThrow(() -> new CommissionTierNotFoundException("Pricing tier not found or inactive: " + request.tierCode()));

        // 3. Load active rules and their options
        List<CommissionPriceConfigEntity> activeConfigs = configRepository.findActiveWithActiveOptions();
        Map<Long, CommissionPriceConfigEntity> configsById = activeConfigs.stream()
                .collect(Collectors.toMap(CommissionPriceConfigEntity::getConfigId, Function.identity()));
        Map<String, CommissionPriceConfigEntity> configsByRuleKey = activeConfigs.stream()
                .collect(Collectors.toMap(CommissionPriceConfigEntity::getRuleKey, Function.identity()));
        Map<Long, CommissionPriceOptionEntity> optionsById = new HashMap<>();
        for (CommissionPriceConfigEntity config : activeConfigs) {
            for (CommissionPriceOptionEntity opt : config.getOptions()) {
                if (opt.isActive()) {
                    optionsById.put(opt.getOptionId(), opt);
                }
            }
        }

        // 4. Validate Step-1 foundational selections
        Map<Long, Long> step1SelectionsByConfig = new HashMap<>();
        List<Step1SelectionSnapshot> step1Snapshots = new ArrayList<>();
        BigDecimal step1Subtotal = BigDecimal.ZERO;

        for (Step1SelectionItem item : request.step1Selections()) {
            CommissionPriceConfigEntity config = configsById.get(item.configId());
            if (config == null) {
                throw new CommissionRuleNotFoundException("Pricing rule config not found: " + item.configId());
            }
            CommissionPriceOptionEntity option = optionsById.get(item.optionId());
            if (option == null) {
                throw new CommissionRuleNotFoundException("Pricing option not found: " + item.optionId());
            }
            if (!Objects.equals(option.getConfig().getConfigId(), config.getConfigId())) {
                throw new InvalidCommissionRuleSelectionException(
                        "Option ID " + item.optionId() + " does not belong to rule " + config.getRuleKey());
            }
            if (step1SelectionsByConfig.putIfAbsent(config.getConfigId(), option.getOptionId()) != null) {
                throw new InvalidCommissionRuleSelectionException(
                        "Duplicate selection for Step-1 rule: " + config.getRuleKey());
            }

            BigDecimal optionPrice = option.getBasePrice() != null ? option.getBasePrice() : BigDecimal.ZERO;
            step1Subtotal = step1Subtotal.add(optionPrice);
            step1Snapshots.add(new Step1SelectionSnapshot(
                    config.getConfigId(),
                    config.getRuleKey(),
                    config.getName(),
                    option.getOptionId(),
                    option.getOptionCode(),
                    option.getOptionLabel(),
                    optionPrice
            ));
        }

        // Validate mandatory Step-1 rules: FORMAT_TYPE and MATRIX_SIZE
        CommissionPriceConfigEntity formatTypeConfig = configsByRuleKey.get("FORMAT_TYPE");
        if (formatTypeConfig == null || !step1SelectionsByConfig.containsKey(formatTypeConfig.getConfigId())) {
            throw new InvalidCommissionRuleSelectionException("FORMAT_TYPE selection is required in Step-1");
        }
        Long selectedFormatOptionId = step1SelectionsByConfig.get(formatTypeConfig.getConfigId());
        CommissionPriceOptionEntity formatOption = optionsById.get(selectedFormatOptionId);

        CommissionPriceConfigEntity matrixSizeConfig = configsByRuleKey.get("MATRIX_SIZE");
        if (matrixSizeConfig == null || !step1SelectionsByConfig.containsKey(matrixSizeConfig.getConfigId())) {
            throw new InvalidCommissionRuleSelectionException("MATRIX_SIZE selection is required in Step-1");
        }
        Long selectedMatrixOptionId = step1SelectionsByConfig.get(matrixSizeConfig.getConfigId());
        CommissionPriceOptionEntity matrixOption = optionsById.get(selectedMatrixOptionId);

        // Conditional rule: FRAME_RANGE
        CommissionPriceConfigEntity frameRangeConfig = configsByRuleKey.get("FRAME_RANGE");
        boolean isAnimatedGif = "ANIMATED_GIF".equalsIgnoreCase(formatOption.getOptionCode());
        boolean hasFrameRangeSelection = frameRangeConfig != null && step1SelectionsByConfig.containsKey(frameRangeConfig.getConfigId());

        if (isAnimatedGif && !hasFrameRangeSelection) {
            throw new InvalidCommissionRuleSelectionException(
                    "FRAME_RANGE selection is required when FORMAT_TYPE is ANIMATED_GIF");
        }
        if (!isAnimatedGif && hasFrameRangeSelection) {
            throw new InvalidCommissionRuleSelectionException(
                    "FRAME_RANGE cannot be selected when FORMAT_TYPE is STATIC_IMAGE");
        }

        // Validate and derive targetMatrixDimensions
        String resolvedTargetMatrixDimensions;
        if (request.targetMatrixDimensions() != null && !request.targetMatrixDimensions().isBlank()) {
            if (!request.targetMatrixDimensions().trim().equalsIgnoreCase(matrixOption.getOptionCode())) {
                throw new InvalidCommissionRuleSelectionException(
                        "targetMatrixDimensions must match selected MATRIX_SIZE: " + matrixOption.getOptionCode());
            }
            resolvedTargetMatrixDimensions = request.targetMatrixDimensions().trim();
        } else {
            resolvedTargetMatrixDimensions = matrixOption.getOptionCode();
        }

        // 5. Stage 2: Preset vs CUSTOM calculation
        BigDecimal initialPrice;
        BigDecimal step2Subtotal = BigDecimal.ZERO;
        List<Step2SelectionSnapshot> step2Snapshots = new ArrayList<>();
        Set<Long> allSelectedOptionIds = new HashSet<>(step1SelectionsByConfig.values());

        // Process Step-2 options
        Map<Long, SelectedOptionItem> step2SelectionsByConfig = new HashMap<>();
        if (request.selectedOptions() != null) {
            for (SelectedOptionItem item : request.selectedOptions()) {
                CommissionPriceConfigEntity config = configsById.get(item.configId());
                if (config == null) {
                    throw new CommissionRuleNotFoundException("Pricing rule config not found: " + item.configId());
                }
                if (config.isSystem()) {
                    throw new InvalidCommissionRuleSelectionException(
                            "Step-1 system rule cannot be submitted as Step-2 option: " + config.getRuleKey());
                }
                if (step2SelectionsByConfig.putIfAbsent(config.getConfigId(), item) != null) {
                    throw new InvalidCommissionRuleSelectionException(
                            "Duplicate selection for Step-2 rule: " + config.getRuleKey());
                }
                if (item.optionIds() != null) {
                    for (Long optId : item.optionIds()) {
                        CommissionPriceOptionEntity opt = optionsById.get(optId);
                        if (opt == null) {
                            throw new CommissionRuleNotFoundException("Pricing option not found: " + optId);
                        }
                        if (!Objects.equals(opt.getConfig().getConfigId(), config.getConfigId())) {
                            throw new InvalidCommissionRuleSelectionException(
                                    "Option ID " + optId + " does not belong to rule " + config.getRuleKey());
                        }
                        allSelectedOptionIds.add(optId);
                    }
                }
            }
        }

        if (!tier.isCustom()) {
            // Preset tier validation
            Set<Long> requiredOptionIds = tier.getIncludedOptions().stream()
                    .map(CommissionPriceOptionEntity::getOptionId)
                    .collect(Collectors.toSet());
            Set<Long> submittedStep1OptionIds = new HashSet<>(step1SelectionsByConfig.values());

            if (!submittedStep1OptionIds.equals(requiredOptionIds)) {
                throw new PresetSelectionMismatchException(
                        "Submitted Step-1 selections do not match preset tier requirements for " + tier.getTierCode());
            }

            // Optional Step-2 add-ons calculation
            step2Subtotal = calculateStep2SubtotalAndSnapshots(
                    step2SelectionsByConfig, configsById, optionsById, step2Snapshots);
            initialPrice = tier.getBasePrice().add(step2Subtotal);

        } else {
            // CUSTOM tier multi-level recursive validation (depth <= 5)
            validateCustomRuleDependencies(activeConfigs, configsById, optionsById, allSelectedOptionIds,
                    step1SelectionsByConfig, step2SelectionsByConfig);

            step2Subtotal = calculateStep2SubtotalAndSnapshots(
                    step2SelectionsByConfig, configsById, optionsById, step2Snapshots);
            initialPrice = tier.getBasePrice().add(step1Subtotal).add(step2Subtotal);
        }

        initialPrice = initialPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPrice = initialPrice;

        // 6. Snapshot price breakdown to JSON
        CommissionPriceBreakdownSnapshot snapshot = new CommissionPriceBreakdownSnapshot(
                tier.getTierCode(),
                tier.getName(),
                tier.isCustom(),
                tier.getBasePrice(),
                tier.getDiscountPercent(),
                tier.getMaxRevisions(),
                step1Snapshots,
                step2Snapshots,
                initialPrice,
                totalPrice
        );

        String priceBreakdownJson;
        try {
            priceBreakdownJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize price breakdown snapshot [customerId={}]", customerId, e);
            priceBreakdownJson = "{}";
        }

        // 7. Auto-assign staff (Round-Robin)
        Long assignedStaffId = staffAutoAssignmentService.assignNextAvailableStaff();

        // 8. Persist CommissionRequestEntity
        CommissionRequestEntity commission = CommissionRequestEntity.createPendingReview(
                customerId,
                request.title(),
                request.description(),
                tier.getTierCode(),
                resolvedTargetMatrixDimensions,
                priceBreakdownJson,
                initialPrice,
                tier.getMaxRevisions(),
                assignedStaffId
        );
        commission = commissionRequestRepository.save(commission);

        // 9. Persist CommissionAttachmentEntity records
        List<CommissionAttachmentEntity> savedAttachments = new ArrayList<>();
        if (request.attachments() != null && !request.attachments().isEmpty()) {
            for (int i = 0; i < request.attachments().size(); i++) {
                CommissionAttachmentItemRequest attReq = request.attachments().get(i);
                CommissionAttachmentEntity attachment = CommissionAttachmentEntity.create(
                        commission.getCommissionId(),
                        attReq.imageUrl(),
                        attReq.caption(),
                        i
                );
                savedAttachments.add(attachmentRepository.save(attachment));
            }
        }

        log.info("Commission request created successfully [commissionId={}] [customerId={}] [status={}] [assignedStaffId={}]",
                commission.getCommissionId(), customerId, commission.getStatus(), assignedStaffId);

        return commissionRequestMapper.toCustomerDetailResponse(commission, savedAttachments, List.of());
    }

    private BigDecimal calculateStep2SubtotalAndSnapshots(
            Map<Long, SelectedOptionItem> step2Selections,
            Map<Long, CommissionPriceConfigEntity> configsById,
            Map<Long, CommissionPriceOptionEntity> optionsById,
            List<Step2SelectionSnapshot> step2Snapshots) {

        BigDecimal subtotal = BigDecimal.ZERO;
        for (SelectedOptionItem item : step2Selections.values()) {
            CommissionPriceConfigEntity config = configsById.get(item.configId());
            BigDecimal itemSubtotal = BigDecimal.ZERO;
            List<OptionSnapshot> optionSnapshots = new ArrayList<>();

            if (item.optionIds() != null && !item.optionIds().isEmpty()) {
                if (config.getSelectionType() == SelectionType.SINGLE_SELECT && item.optionIds().size() > 1) {
                    throw new InvalidCommissionRuleSelectionException(
                            "Rule '" + config.getRuleKey() + "' only allows a single option selection");
                }
                for (Long optId : item.optionIds()) {
                    CommissionPriceOptionEntity opt = optionsById.get(optId);
                    BigDecimal price = opt.getBasePrice() != null ? opt.getBasePrice() : BigDecimal.ZERO;
                    itemSubtotal = itemSubtotal.add(price);
                    optionSnapshots.add(new OptionSnapshot(
                            opt.getOptionId(),
                            opt.getOptionCode(),
                            opt.getOptionLabel(),
                            price
                    ));
                }
            }

            subtotal = subtotal.add(itemSubtotal);
            step2Snapshots.add(new Step2SelectionSnapshot(
                    config.getConfigId(),
                    config.getRuleKey(),
                    config.getName(),
                    optionSnapshots,
                    item.textValue(),
                    itemSubtotal
            ));
        }
        return subtotal;
    }

    private void validateCustomRuleDependencies(
            List<CommissionPriceConfigEntity> activeConfigs,
            Map<Long, CommissionPriceConfigEntity> configsById,
            Map<Long, CommissionPriceOptionEntity> optionsById,
            Set<Long> allSelectedOptionIds,
            Map<Long, Long> step1Selections,
            Map<Long, SelectedOptionItem> step2Selections) {

        for (CommissionPriceConfigEntity config : activeConfigs) {
            boolean isStep1 = config.isSystem();
            boolean hasSelection = isStep1
                    ? step1Selections.containsKey(config.getConfigId())
                    : step2Selections.containsKey(config.getConfigId());

            boolean isEligible = isRuleEligible(config, configsById, optionsById, allSelectedOptionIds, 0);

            if (!isEligible && hasSelection) {
                throw new InvalidCommissionRuleSelectionException(
                        "Rule '" + config.getRuleKey() + "' is not eligible because its prerequisite option chain was not selected");
            }

            if (isEligible && config.isRequired() && !hasSelection) {
                throw new InvalidCommissionRuleSelectionException(
                        "Required rule '" + config.getRuleKey() + "' must have a selection");
            }
        }
    }

    private boolean isRuleEligible(
            CommissionPriceConfigEntity config,
            Map<Long, CommissionPriceConfigEntity> configsById,
            Map<Long, CommissionPriceOptionEntity> optionsById,
            Set<Long> allSelectedOptionIds,
            int depth) {

        if (depth > MAX_DEPENDENCY_DEPTH) {
            throw new InvalidCommissionRuleSelectionException("Rule dependency chain exceeds maximum allowed depth of " + MAX_DEPENDENCY_DEPTH);
        }

        if (config.getDependsOnOptionId() == null) {
            return true;
        }

        Long parentOptionId = config.getDependsOnOptionId();
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

        return isRuleEligible(parentConfig, configsById, optionsById, allSelectedOptionIds, depth + 1);
    }
}
