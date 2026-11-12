package com.lumintra.backend.store.domain.service;

import com.lumintra.backend.store.domain.model.ProductSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductLookupService {
    Optional<ProductSnapshot> findSnapshotById(Long productId);

    Map<Long, ProductSnapshot> findSnapshotsByIds(Collection<Long> productIds);

    List<ProductSnapshot> findAllSnapshots();
}
