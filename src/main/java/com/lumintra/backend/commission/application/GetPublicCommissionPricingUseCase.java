package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.PublicCommissionPricingResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionPricingMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceConfigRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPriceOptionRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionPricingTierRepository;
import com.lumintra.backend.shared.exception.commission.CommissionTierNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPublicCommissionPricingUseCase {

    private final CommissionPricingTierRepository tierRepository;
    private final CommissionPriceConfigRepository configRepository;
    private final CommissionPriceOptionRepository optionRepository;
    private final CommissionPricingMapper pricingMapper;

    public PublicCommissionPricingResponse execute() {
        List<CommissionPricingTierEntity> activeTiers = tierRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();

        CommissionPricingTierEntity customTier = activeTiers.stream()
                .filter(CommissionPricingTierEntity::isCustom)
                .findFirst()
                .orElseThrow(() -> new CommissionTierNotFoundException("Active CUSTOM tier configuration not found."));

        List<CommissionPriceConfigEntity> activeConfigs = configRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
        List<CommissionPriceOptionEntity> allActiveOptions = optionRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();

        Map<Long, CommissionPriceOptionEntity> optionLookup = allActiveOptions.stream()
                .filter(o -> o.getOptionId() != null)
                .collect(Collectors.toMap(CommissionPriceOptionEntity::getOptionId, Function.identity(), (a, b) -> a));

        return pricingMapper.toPublicPricingResponse(activeTiers, customTier, activeConfigs, optionLookup);
    }
}
