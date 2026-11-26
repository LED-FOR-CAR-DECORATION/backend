package com.lumintra.backend.store;

import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreCacheEvictorTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache productsCache;

    @Mock
    private Cache productCache;

    @InjectMocks
    private StoreCacheEvictor storeCacheEvictor;

    @Test
    @DisplayName("UTCID01 [N]: evictAllProducts clears products list cache")
    void evictAllProducts_clearsCache() {
        when(cacheManager.getCache(StoreCacheNames.PRODUCTS)).thenReturn(productsCache);

        storeCacheEvictor.evictAllProducts();

        verify(productsCache).clear();
    }

    @Test
    @DisplayName("UTCID02 [A]: evictAllProducts handles cache exception gracefully")
    void evictAllProducts_handlesExceptionGracefully() {
        when(cacheManager.getCache(StoreCacheNames.PRODUCTS)).thenThrow(new RuntimeException("Redis connection refused"));

        assertThatCode(() -> storeCacheEvictor.evictAllProducts()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("UTCID03 [N]: evictProductAndAllProducts evicts product and clears products list cache")
    void evictProductAndAllProducts_evictsBoth() {
        when(cacheManager.getCache(StoreCacheNames.PRODUCT)).thenReturn(productCache);
        when(cacheManager.getCache(StoreCacheNames.PRODUCTS)).thenReturn(productsCache);

        storeCacheEvictor.evictProductAndAllProducts(123L);

        verify(productCache).evict(123L);
        verify(productsCache).clear();
    }
}
