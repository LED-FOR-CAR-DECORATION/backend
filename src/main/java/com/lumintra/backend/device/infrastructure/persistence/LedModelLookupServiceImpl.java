package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedModelLookupServiceImpl implements LedModelLookupService {

    private final LedModelRepository ledModelRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<LedModelSnapshot> findByModelId(Long modelId) {
        if (modelId == null) {
            return Optional.empty();
        }
        return ledModelRepository.findById(modelId).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, LedModelSnapshot> findByModelIds(Collection<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return ledModelRepository.findAllById(modelIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(
                        LedModelSnapshot::modelId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private LedModelSnapshot toSnapshot(LedModelEntity entity) {
        return new LedModelSnapshot(
                entity.getModelId(),
                entity.getModelName(),
                entity.getSpec(),
                entity.getStockQuantity() != null ? entity.getStockQuantity() : 0L
        );
    }
}
