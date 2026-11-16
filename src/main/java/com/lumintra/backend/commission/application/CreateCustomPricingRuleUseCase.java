package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.CreatePricingRuleRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.domain.service.PricingInvariantValidator;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.shared.exception.commission.DuplicateCommissionRuleException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateCustomPricingRuleUseCase {

    private final CommissionPriceConfigRepository configRepository;
    private final CommissionPriceOptionRepository optionRepository;
    private final PricingInvariantValidator invariantValidator;
    private final CommissionPricingMapper pricingMapper;

    @Transactional
    public ManagementPricingRuleResponse execute(CreatePricingRuleRequest request) {
        if (request == null) {
            throw new InvalidRuleConfigurationException("CreatePricingRuleRequest cannot be null.");
        }

        String ruleKey = invariantValidator.normalizeAndValidateRuleKey(request.configCode());
        if (configRepository.existsByRuleKeyIgnoreCase(ruleKey)) {
            throw new DuplicateCommissionRuleException(
                    String.format("Commission pricing rule with code '%s' already exists.", ruleKey)
            );
        }

        CommissionPriceOptionEntity parentOption = resolveParentOption(request.dependsOnOptionId());

        List<CommissionPriceOptionEntity> optionEntities = request.options() == null
                ? List.of()
                : request.options().stream()
                        .map(o -> CommissionPriceOptionEntity.create(
                                o.optionCode(),
                                o.optionLabel(),
                                o.basePrice(),
                                o.isDefault(),
                                o.displayOrder()
                        ))
                        .toList();

        CommissionPriceConfigEntity configEntity = CommissionPriceConfigEntity.createCustom(
                ruleKey,
                request.configName(),
                request.description(),
                request.selectionType(),
                request.uiType(),
                request.isRequired(),
                request.displayOrder(),
                request.dependsOnOptionId(),
                optionEntities
        );

        invariantValidator.validateCustomRuleCreation(configEntity);
        CommissionPriceConfigEntity saved = configRepository.save(configEntity);
        return pricingMapper.toManagementRuleDetail(saved, parentOption);
    }

    private CommissionPriceOptionEntity resolveParentOption(Long dependsOnOptionId) {
        if (dependsOnOptionId == null) {
            return null;
        }
        CommissionPriceOptionEntity parentOption = optionRepository.findById(dependsOnOptionId)
                .orElseThrow(() -> new InvalidRuleConfigurationException(
                        String.format("Parent option with ID %d does not exist.", dependsOnOptionId)
                ));
        invariantValidator.validateParentOptionDependency(parentOption);
        return parentOption;
    }
}
