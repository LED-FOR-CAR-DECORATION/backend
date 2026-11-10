package com.lumintra.backend.store;

import com.lumintra.backend.shared.caching.CustomCacheErrorHandler;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.api.response.ProductListResponse;
import com.lumintra.backend.store.application.ListProductsUseCase;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheNames;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = {
        ListProductsUseCaseCachingTest.TestConfig.class,
        ListProductsUseCase.class,
        StoreCacheEvictor.class
})
class ListProductsUseCaseCachingTest {

    @Autowired
    private ListProductsUseCase listProductsUseCase;

    @Autowired
    private StoreCacheEvictor storeCacheEvictor;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private FailingCacheHolder failingCacheHolder;

    private ProductListResponse mockResponse;

    @BeforeEach
    void setUp() {
        reset(productRepository, productMapper);
        failingCacheHolder.setSimulateOutage(false);

        Cache cache = cacheManager.getCache(StoreCacheNames.PRODUCTS);
        if (cache != null) {
            cache.clear();
        }

        mockResponse = ProductListResponse.builder()
                .items(Collections.emptyList())
                .meta(PaginationMeta.builder()
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build())
                .build();

        Page emptyPage = new PageImpl<>(List.of());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);
        when(productMapper.toProductListResponse(any())).thenReturn(mockResponse);
    }

    @Test
    @DisplayName("UTCID01 [N]: Cache miss invokes database repository and caches response")
    void cacheMiss_invokesRepositoryAndCaches() {
        ProductFilterRequest request = new ProductFilterRequest();

        ProductListResponse result = listProductsUseCase.execute(request);

        assertThat(result).isNotNull();
        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

        Cache cache = cacheManager.getCache(StoreCacheNames.PRODUCTS);
        assertThat(cache).isNotNull();
        assertThat(cache.get(request.toCacheKey())).isNotNull();
    }

    @Test
    @DisplayName("UTCID02 [N]: Cache hit serves from cache without querying database repository")
    void cacheHit_servesFromCacheWithoutRepositoryCall() {
        ProductFilterRequest request = new ProductFilterRequest();

        // 1st call: Cache miss -> queries DB
        listProductsUseCase.execute(request);
        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

        // 2nd call: Cache hit -> does not query DB
        ProductListResponse cachedResult = listProductsUseCase.execute(request);
        assertThat(cachedResult).isNotNull();
        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("UTCID03 [N]: Cache invalidation clears entries and next call re-fetches from database")
    void cacheInvalidation_clearsCacheAndRefetchesFromDb() {
        ProductFilterRequest request = new ProductFilterRequest();

        // 1st call: queries DB and caches
        listProductsUseCase.execute(request);
        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));

        // Invalidate cache
        storeCacheEvictor.evictAllProducts();

        // 2nd call: Cache was cleared, queries DB again
        listProductsUseCase.execute(request);
        verify(productRepository, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("UTCID04 [A]: Cache outage degrades gracefully to database without throwing 500")
    void cacheOutage_degradesGracefullyToDatabase() {
        ProductFilterRequest request = new ProductFilterRequest();

        // Simulate Redis down / connection timeout
        failingCacheHolder.setSimulateOutage(true);

        ProductListResponse result = listProductsUseCase.execute(request);

        assertThat(result).isNotNull();
        // Database repository is still queried to serve user data
        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    // --- Test Spring Configuration ---
    static class FailingCacheHolder {
        private boolean simulateOutage = false;

        public boolean isSimulateOutage() {
            return simulateOutage;
        }

        public void setSimulateOutage(boolean simulateOutage) {
            this.simulateOutage = simulateOutage;
        }
    }

    @Configuration
    @EnableCaching
    static class TestConfig implements CachingConfigurer {

        @Bean
        public ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        public ProductMapper productMapper() {
            return mock(ProductMapper.class);
        }

        @Bean
        public FailingCacheHolder failingCacheHolder() {
            return new FailingCacheHolder();
        }

        @Bean
        public CacheManager cacheManager(FailingCacheHolder failingCacheHolder) {
            return new ConcurrentMapCacheManager() {
                @Override
                protected Cache createConcurrentMapCache(String name) {
                    return new ConcurrentMapCache(name) {
                        @Override
                        public ValueWrapper get(Object key) {
                            if (failingCacheHolder.isSimulateOutage()) {
                                throw new RuntimeException("Redis connection refused / timed out (Simulated Outage)");
                            }
                            return super.get(key);
                        }

                        @Override
                        public void put(Object key, Object value) {
                            if (failingCacheHolder.isSimulateOutage()) {
                                throw new RuntimeException("Redis write timeout (Simulated Outage)");
                            }
                            super.put(key, value);
                        }
                    };
                }
            };
        }

        @Override
        public CacheErrorHandler errorHandler() {
            return new CustomCacheErrorHandler();
        }
    }
}
