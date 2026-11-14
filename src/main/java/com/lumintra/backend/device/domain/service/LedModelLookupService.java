package com.lumintra.backend.device.domain.service;

import com.lumintra.backend.device.domain.model.LedModelSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface LedModelLookupService {

    Optional<LedModelSnapshot> findByModelId(Long modelId);

    Map<Long, LedModelSnapshot> findByModelIds(Collection<Long> modelIds);
}
