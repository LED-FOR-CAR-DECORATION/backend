package com.lumintra.backend.store.infrastructure.caching;

import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class StoreProductUpdatedCacheListener {
    private final StoreCacheEvictor evictor;

    @TransactionalEventListener
    public void afterCommit(StoreProductUpdatedEvent event) {
        evictor.evictProductAndAllProducts(event.productId());
    }
}
