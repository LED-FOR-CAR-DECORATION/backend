package com.lumintra.backend.device.domain.service;

import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;

import java.util.Collection;
import java.util.Map;

/**
 * Read-only cross-module boundary for retrieving linked order fulfillment records per LED instance.
 */
public interface OrderFulfillmentLookupService {

    Map<Long, InstanceFulfillmentSnapshot> findFulfillmentsByInstanceIds(Collection<Long> instanceIds);
}
