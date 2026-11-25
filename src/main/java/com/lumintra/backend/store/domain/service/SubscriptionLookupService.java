package com.lumintra.backend.store.domain.service;

import com.lumintra.backend.store.domain.model.SubscriptionDetailSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface SubscriptionLookupService {
    Optional<SubscriptionDetailSnapshot> findByProductId(Long productId);

    Map<Long, SubscriptionDetailSnapshot> findByProductIds(Collection<Long> productIds);
}
