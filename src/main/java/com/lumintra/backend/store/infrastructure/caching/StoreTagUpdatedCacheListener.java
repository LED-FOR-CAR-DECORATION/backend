package com.lumintra.backend.store.infrastructure.caching;

import com.lumintra.backend.store.domain.event.StoreTagUpdatedEvent;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class StoreTagUpdatedCacheListener {
    private static final Logger log = LoggerFactory.getLogger(StoreTagUpdatedCacheListener.class);

    private final CacheManager caches;

    @TransactionalEventListener
    public void afterCommit(StoreTagUpdatedEvent event) {
        clear(StoreCacheNames.TAGS);
        clear(StoreCacheNames.PRODUCTS);
        clear(StoreCacheNames.PRODUCT);
    }

    private void clear(String namespace) {
        try {
            var cache = caches.getCache(namespace);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to evict cache [namespace={}]", namespace, ex);
        }
    }
}
