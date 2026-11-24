package com.lumintra.backend.device.domain.service;

import com.lumintra.backend.device.domain.model.LedModelSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface LedModelLookupService {

    Optional<LedModelSnapshot> findByModelId(Long modelId);

    default Optional<Long> findProductIdByModelId(Long modelId) {
        return findByModelId(modelId).map(LedModelSnapshot::modelId);
    }

    default Optional<LedModelSnapshot> findByProductId(Long productId) {
        return findByModelId(productId);
    }

    Map<Long, LedModelSnapshot> findByModelIds(Collection<Long> modelIds);
}
