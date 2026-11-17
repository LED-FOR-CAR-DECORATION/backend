package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UpdatePricingRuleRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.shared.exception.commission.CommissionRuleNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdatePricingRuleUseCase {
    private final CommissionPriceConfigRepository configRepository;
    private final PricingInvariantValidator invariantValidator;
    private final GetManagementPricingRuleDetailUseCase getRuleDetail;

    @Transactional
    public ManagementPricingRuleResponse execute(Long ruleId, UpdatePricingRuleRequest request) {
        CommissionPriceConfigEntity rule = configRepository.findById(ruleId)
                .orElseThrow(() -> new CommissionRuleNotFoundException(
                        "Commission pricing rule with ID " + ruleId + " not found."));
        Map<Long, CommissionPriceOptionEntity> optionsById = new HashMap<>();
        for (CommissionPriceOptionEntity option : rule.getOptions()) {
            optionsById.put(option.getOptionId(), option);
        }

        rule.setName(request.configName().trim());
        rule.setDescription(request.description() == null ? null : request.description().trim());
        if (!rule.isSystem()) {
            rule.setRequired(request.isRequired());
        }
        for (UpdatePricingRuleRequest.OptionUpdate update : request.options()) {
            CommissionPriceOptionEntity option = optionsById.remove(update.optionId());
            if (option == null) {
                throw new InvalidRuleConfigurationException("Invalid or duplicate option ID: " + update.optionId());
            }
            option.setOptionLabel(update.optionLabel().trim());
            option.setBasePrice(update.basePrice());
        }

        List<UpdatePricingRuleRequest.NewOption> newOptions = request.newOptions();
        if (newOptions != null && !newOptions.isEmpty()) {
            appendOptions(rule, newOptions);
        }

        if (rule.isSystem()) {
            invariantValidator.validateRule(rule);
        } else {
            invariantValidator.validateCustomRuleCreation(rule);
        }
        if (newOptions != null && !newOptions.isEmpty()) {
            configRepository.flush();
        }
        return getRuleDetail.execute(ruleId);
    }

    private void appendOptions(CommissionPriceConfigEntity rule, List<UpdatePricingRuleRequest.NewOption> additions) {
        if ("FORMAT_TYPE".equals(rule.getRuleKey())
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
            if ("MATRIX_SIZE".equals(rule.getRuleKey())) {
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
                    code, addition.optionLabel(), ModifierType.FLAT_ADD, addition.basePrice(), false, ++displayOrder));
        }
    }
}
