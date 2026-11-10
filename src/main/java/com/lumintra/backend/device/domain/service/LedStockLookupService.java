package com.lumintra.backend.device.domain.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface LedStockLookupService {

    Optional<Long> findStockByModelId(Long modelId);

    Map<Long, Long> findStocksByModelIds(Collection<Long> modelIds);
}
