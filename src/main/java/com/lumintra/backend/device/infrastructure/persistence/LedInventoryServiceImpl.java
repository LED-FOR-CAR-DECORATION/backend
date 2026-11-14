package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.device.domain.service.LedInventoryService;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class LedInventoryServiceImpl implements LedInventoryService {

    private static final Logger log = LoggerFactory.getLogger(LedInventoryServiceImpl.class);

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

    @Override
    @Transactional
    public void restoreStocks(Map<Long, Long> quantitiesByModelId) {
        if (quantitiesByModelId == null || quantitiesByModelId.isEmpty()) {
            return;
        }

        // Guard against null keys before sorting ascending to preserve deterministic row-lock ordering
        Map<Long, Long> orderedQuantities = new TreeMap<>();
        for (Map.Entry<Long, Long> entry : quantitiesByModelId.entrySet()) {
            Long modelId = entry.getKey();
            Long quantity = entry.getValue();
            if (modelId != null && quantity != null && quantity > 0) {
                orderedQuantities.merge(modelId, quantity, Long::sum);
            }
        }

        for (Map.Entry<Long, Long> entry : orderedQuantities.entrySet()) {
            int updatedRows = ledModelRepository.restoreStock(entry.getKey(), entry.getValue());
            if (updatedRows == 0) {
                log.warn("Skipped LED stock restoration because hardware model no longer exists [modelId={}] [quantity={}]",
                        entry.getKey(), entry.getValue());
            }
        }
    }
}
