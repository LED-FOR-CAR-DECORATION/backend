package com.lumintra.backend.store.infrastructure.caching;

import com.lumintra.backend.store.domain.event.StoreTagCreatedEvent;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class StoreTagCreatedCacheListener {
    private static final Logger log = LoggerFactory.getLogger(StoreTagCreatedCacheListener.class);

    private final CacheManager caches;

    @TransactionalEventListener
    public void afterCommit(StoreTagCreatedEvent event) {
        try {
            var cache = caches.getCache(StoreCacheNames.TAGS);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to evict store tag cache", ex);
        }
    }
}
