package com.lumintra.backend.store.infrastructure.persistence;

import com.lumintra.backend.store.domain.model.SubscriptionDetailSnapshot;
import com.lumintra.backend.store.domain.service.SubscriptionLookupService;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
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
public class SubscriptionLookupServiceImpl implements SubscriptionLookupService {

    private final SubscriptionDetailRepository subscriptionDetailRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<SubscriptionDetailSnapshot> findByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return subscriptionDetailRepository.findByProductId(productId).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, SubscriptionDetailSnapshot> findByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return subscriptionDetailRepository.findByProductIdIn(productIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(
                        SubscriptionDetailSnapshot::productId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private SubscriptionDetailSnapshot toSnapshot(SubscriptionDetailEntity entity) {
        return new SubscriptionDetailSnapshot(
                entity.getProductId(),
                entity.getPlanCode(),
                entity.getPlanName(),
                entity.isLifetime(),
                entity.getDurationDays(),
                entity.isAllAccessCharacters(),
                entity.getMaxCommissionRevisions(),
                entity.getMaxMobileProfiles()
        );
    }
}
