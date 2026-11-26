package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionRuleNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagementPricingRuleDetailUseCase {

    private final CommissionPriceConfigRepository configRepository;
    private final CommissionPriceOptionRepository optionRepository;
    private final CommissionPricingMapper pricingMapper;

    public ManagementPricingRuleResponse execute(Long ruleId) {
        if (ruleId == null || ruleId <= 0) {
            throw new InvalidRuleConfigurationException("ruleId must be a positive integer.");
        }

        CommissionPriceConfigEntity config = configRepository.findById(ruleId)
                .orElseThrow(() -> new CommissionRuleNotFoundException(
                        String.format("Commission pricing rule with ID %d not found.", ruleId)
                ));

        CommissionPriceOptionEntity parentOption = config.getDependsOnOptionId() != null
                ? optionRepository.findById(config.getDependsOnOptionId()).orElse(null)
                : null;

        return pricingMapper.toManagementRuleDetail(config, parentOption);
    }
}
