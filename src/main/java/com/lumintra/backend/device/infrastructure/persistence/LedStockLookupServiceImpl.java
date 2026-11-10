package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.device.domain.service.LedStockLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedStockLookupServiceImpl implements LedStockLookupService {

    private final LedModelRepository ledModelRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findStockByModelId(Long modelId) {
        if (modelId == null) {
            return Optional.empty();
        }
        return ledModelRepository.findById(modelId).map(LedModelEntity::getStockQuantity);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> findStocksByModelIds(Collection<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return ledModelRepository.findAllById(modelIds).stream()
                .collect(Collectors.toMap(
                        LedModelEntity::getModelId,
                        entity -> entity.getStockQuantity() != null ? entity.getStockQuantity() : 0L,
                        (existing, replacement) -> existing
                ));
    }
}
