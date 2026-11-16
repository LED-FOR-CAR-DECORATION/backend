package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ManagementCommissionPricingResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagementCommissionPricingUseCase {

    private final CommissionPricingTierRepository tierRepository;
    private final CommissionPriceConfigRepository configRepository;
    private final CommissionPricingMapper pricingMapper;

    public ManagementCommissionPricingResponse execute() {
        List<CommissionPricingTierEntity> allTiers = tierRepository.findAllByOrderByDisplayOrderAsc();

        CommissionPricingTierEntity activeCustomTier = allTiers.stream()
                .filter(t -> t.isCustom() && t.isActive())
                .findFirst()
                .orElse(null);

        List<CommissionPriceConfigEntity> allConfigs = configRepository.findAllByOrderByDisplayOrderAsc();

        return pricingMapper.toManagementPricingResponse(allTiers, activeCustomTier, allConfigs);
    }
}
