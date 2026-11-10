package com.lumintra.backend.store.domain.service;

import com.lumintra.backend.store.domain.model.ProductSnapshot;

import java.util.Collection;
import java.util.Map;

public interface ProductLookupService {
    Map<Long, ProductSnapshot> findSnapshotsByIds(Collection<Long> productIds);
}
