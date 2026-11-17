package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UpdatePricingRuleRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UpdateCustomPricingRuleUseCase {
    private final CommissionPriceConfigRepository configRepository;
    private final PricingInvariantValidator invariantValidator;
    private final GetManagementPricingRuleDetailUseCase getRuleDetail;

    @Transactional
    public ManagementPricingRuleResponse execute(Long ruleId, UpdatePricingRuleRequest request) {
        CommissionPriceConfigEntity rule = configRepository.findById(ruleId)
                .orElseThrow(() -> new CommissionRuleNotFoundException(
                        "Commission pricing rule with ID " + ruleId + " not found."));
        if (rule.isSystem()) {
            throw new InvalidRuleConfigurationException("System pricing rules cannot be edited.");
        }

        Map<Long, CommissionPriceOptionEntity> optionsById = new HashMap<>();
        for (CommissionPriceOptionEntity option : rule.getOptions()) {
            optionsById.put(option.getOptionId(), option);
        }

        rule.setName(request.configName().trim());
        rule.setDescription(request.description() == null ? null : request.description().trim());
        rule.setRequired(request.isRequired());
        for (UpdatePricingRuleRequest.OptionUpdate update : request.options()) {
            CommissionPriceOptionEntity option = optionsById.remove(update.optionId());
            if (option == null) {
                throw new InvalidRuleConfigurationException("Invalid or duplicate option ID: " + update.optionId());
            }
            option.setOptionLabel(update.optionLabel().trim());
            option.setBasePrice(update.basePrice());
        }

        invariantValidator.validateCustomRuleCreation(rule);
        return getRuleDetail.execute(ruleId);
    }
}
