package com.lumintra.backend.store.infrastructure.caching;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(StoreCacheEvictor.class);

    private final CacheManager cacheManager;

    /**
     * Evicts all product list cache entries when products or categories are modified/created/deleted.
     */
    public void evictAllProducts() {
        try {
            Cache cache = cacheManager.getCache(StoreCacheNames.PRODUCTS);
            if (cache != null) {
                cache.clear();
                log.info("Product list caches evicted [namespace={}]", StoreCacheNames.PRODUCTS);
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to evict product list cache [namespace={}] [error={}]",
                    StoreCacheNames.PRODUCTS, ex.getMessage());
        }
    }

    /**
     * Evicts single product detail cache and all product list caches.
     *
     * @param productId ID of the updated or removed product
     */
    public void evictProductAndAllProducts(Long productId) {
        try {
            Cache productCache = cacheManager.getCache(StoreCacheNames.PRODUCT);
            if (productCache != null && productId != null) {
                productCache.evict(productId);
                log.info("Product cache evicted [productId={}]", productId);
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to evict product cache [productId={}] [error={}]",
                    productId, ex.getMessage());
        }
        evictAllProducts();
    }
}
