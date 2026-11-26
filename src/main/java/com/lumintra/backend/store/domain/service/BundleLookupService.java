package com.lumintra.backend.store.domain.service;

import com.lumintra.backend.store.domain.model.BundleSnapshot;

import java.util.Optional;

public interface BundleLookupService {
    Optional<BundleSnapshot> findByProductId(Long productId);
}
