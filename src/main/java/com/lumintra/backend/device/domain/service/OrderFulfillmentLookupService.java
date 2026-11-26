package com.lumintra.backend.device.domain.service;

import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cross-module boundary for retrieving and seeding linked order fulfillment records per LED instance.
 */
public interface OrderFulfillmentLookupService {

    Map<Long, InstanceFulfillmentSnapshot> findFulfillmentsByInstanceIds(Collection<Long> instanceIds);

    default Optional<InstanceFulfillmentSnapshot> findFulfillmentByInstanceId(Long instanceId) {
        if (instanceId == null) {
            return Optional.empty();
        }
        Map<Long, InstanceFulfillmentSnapshot> map = findFulfillmentsByInstanceIds(List.of(instanceId));
        return map != null ? Optional.ofNullable(map.get(instanceId)) : Optional.empty();
    }

    default void seedOrderFulfillmentIfPresent(String transactionCodeSuffix, Long instanceId) {
        // Optional hook for linking seeded LED instances to seeded Commerce orders
    }
}
