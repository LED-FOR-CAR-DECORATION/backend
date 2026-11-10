package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.device.domain.service.LedInventoryService;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class LedInventoryServiceImpl implements LedInventoryService {

    private final LedModelRepository ledModelRepository;

    @Override
    @Transactional
    public boolean deductStocksIfAvailable(Map<Long, Long> requiredQuantitiesByModelId) {
        if (requiredQuantitiesByModelId == null || requiredQuantitiesByModelId.isEmpty()) {
            return true;
        }

        // Sort model IDs ascending to guarantee deterministic row-lock ordering across concurrent transactions
        Map<Long, Long> orderedQuantities = new TreeMap<>(requiredQuantitiesByModelId);
        for (Map.Entry<Long, Long> entry : orderedQuantities.entrySet()) {
            Long modelId = entry.getKey();
            Long quantity = entry.getValue();
            if (modelId == null || quantity == null || quantity <= 0) {
                return false;
            }
            int updatedRows = ledModelRepository.deductStockIfAvailable(modelId, quantity);
            if (updatedRows == 0) {
                return false;
            }
        }
        return true;
    }
}
