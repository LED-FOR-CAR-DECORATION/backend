package com.lumintra.backend.commerce.infrastructure.persistence;

import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Temporary stub until issue #176 introduces {@code order_item_fulfillment} persistence and mapping.
 */
@Service
public class OrderFulfillmentLookupServiceImpl implements OrderFulfillmentLookupService {

    @Override
    public Map<Long, InstanceFulfillmentSnapshot> findFulfillmentsByInstanceIds(Collection<Long> instanceIds) {
        return Collections.emptyMap();
    }
}
