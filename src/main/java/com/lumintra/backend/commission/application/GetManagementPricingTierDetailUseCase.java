package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.shared.exception.commission.CommissionTierNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagementPricingTierDetailUseCase {

    private final CommissionPricingTierRepository tierRepository;
    private final CommissionPricingMapper pricingMapper;

    public ManagementPricingTierResponse execute(Long tierId) {
        if (tierId == null || tierId <= 0) {
            throw new InvalidRuleConfigurationException("tierId must be a positive integer.");
        }

        CommissionPricingTierEntity tier = tierRepository.findById(tierId)
                .orElseThrow(() -> new CommissionTierNotFoundException(
                        String.format("Commission pricing tier with ID %d not found.", tierId)
                ));

        CommissionPricingTierEntity activeCustomTier = tier.isCustom()
                ? tier
                : tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc().stream()
                        .filter(CommissionPricingTierEntity::isCustom)
                        .findFirst()
                        .orElse(null);

        return pricingMapper.toManagementTierDetail(tier, activeCustomTier);
    }
}
