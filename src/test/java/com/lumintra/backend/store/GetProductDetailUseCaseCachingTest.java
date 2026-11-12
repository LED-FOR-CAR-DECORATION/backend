package com.lumintra.backend.store;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.shared.caching.CustomCacheErrorHandler;
import com.lumintra.backend.store.api.response.ProductDetailResponse;
import com.lumintra.backend.store.application.GetProductDetailUseCase;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheNames;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
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
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {
        GetProductDetailUseCaseCachingTest.TestConfig.class,
        GetProductDetailUseCase.class,
        StoreCacheEvictor.class,
        ProductMapper.class
})
class GetProductDetailUseCaseCachingTest {

    @Autowired
    private GetProductDetailUseCase getProductDetailUseCase;

    @Autowired
    private StoreCacheEvictor storeCacheEvictor;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LedModelLookupService ledModelLookupService;

    @Autowired
    private CharacterLookupService characterLookupService;

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private BundleItemRepository bundleItemRepository;

    @Autowired
    private SubscriptionDetailRepository subscriptionDetailRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private FailingCacheHolder failingCacheHolder;

    private ProductEntity sampleLedEntity;

    @BeforeEach
    void setUp() {
        reset(
                productRepository,
                ledModelLookupService,
                characterLookupService,
                bundleRepository,
                bundleItemRepository,
                subscriptionDetailRepository
        );
        failingCacheHolder.setSimulateOutage(false);

        Cache cache = cacheManager.getCache(StoreCacheNames.PRODUCT);
        if (cache != null) {
            cache.clear();
        }

        sampleLedEntity = ProductEntity.of(
                5L,
                "Den LED Matrix Lumintra v1",
                ProductType.LED,
                new BigDecimal("200000.00"),
                "Compact 64x64 windshield LED",
                "https://cdn.lumintra.com/products/led-v1.png",
                List.of("https://cdn.lumintra.com/products/led-1.png"),
                ProductStatus.PUBLISHED,
                Set.of(),
                Instant.parse("2026-10-01T10:00:00Z")
        );

        when(productRepository.findWithTagsByProductIdAndStatusIn(eq(5L), any(Collection.class)))
                .thenReturn(Optional.of(sampleLedEntity));
        when(ledModelLookupService.findByModelId(5L))
                .thenReturn(Optional.of(new LedModelSnapshot(5L, "Matrix 64x64", Map.of("dimensions", "64x64"), 10L)));
    }

    @Test
    @DisplayName("UTCID01 [N]: Cache miss queries DB and populates store:product:{id}")
    void cacheMiss_queriesDatabaseAndStoresInProductCache() {
        ProductDetailResponse response = getProductDetailUseCase.execute(5L);

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(5L);
        verify(productRepository, times(1)).findWithTagsByProductIdAndStatusIn(eq(5L), any());
        verify(ledModelLookupService, times(1)).findByModelId(5L);

        Cache cache = cacheManager.getCache(StoreCacheNames.PRODUCT);
        assertThat(cache).isNotNull();
        assertThat(cache.get(5L, ProductDetailResponse.class)).isEqualTo(response);
    }

    @Test
    @DisplayName("UTCID02 [N]: Cache hit returns cached ProductDetailResponse without querying DB or child services")
    void cacheHit_skipsDatabaseQueries() {
        ProductDetailResponse first = getProductDetailUseCase.execute(5L);
        ProductDetailResponse second = getProductDetailUseCase.execute(5L);

        assertThat(second).isEqualTo(first);
        verify(productRepository, times(1)).findWithTagsByProductIdAndStatusIn(eq(5L), any());
        verify(ledModelLookupService, times(1)).findByModelId(5L);
    }

    @Test
    @DisplayName("UTCID03 [N]: StoreCacheEvictor.evictProductAndAllProducts clears store:product:{id} so next call reloads DB")
    void cacheEviction_invalidatesSingleProductAndRefetches() {
        getProductDetailUseCase.execute(5L);
        verify(productRepository, times(1)).findWithTagsByProductIdAndStatusIn(eq(5L), any());

        storeCacheEvictor.evictProductAndAllProducts(5L);

        getProductDetailUseCase.execute(5L);
        verify(productRepository, times(2)).findWithTagsByProductIdAndStatusIn(eq(5L), any());
    }

    @Test
    @DisplayName("UTCID04 [A]: Redis/Cache outage falls back gracefully to DB via CustomCacheErrorHandler")
    void cacheOutage_degradesGracefullyToDatabase() {
        failingCacheHolder.setSimulateOutage(true);

        ProductDetailResponse response = getProductDetailUseCase.execute(5L);

        assertThat(response).isNotNull();
        assertThat(response.ledDetails().modelName()).isEqualTo("Matrix 64x64");
        verify(productRepository, times(1)).findWithTagsByProductIdAndStatusIn(eq(5L), any());
    }

    @Test
    @DisplayName("UTCID05 [N]: RedisSerializer.json() round-trips nested ProductDetailResponse Java records cleanly")
    void redisJsonSerializer_roundTripsNestedRecordsAndInstantCleanly() {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) RedisSerializer.json();

        ProductDetailResponse original = ProductDetailResponse.builder()
                .productId(12L)
                .productName("Starter Neon Combo")
                .description("Bundle package")
                .productType(ProductType.BUNDLE)
                .basePrice(new BigDecimal("450000.00"))
                .status(ProductStatus.PUBLISHED)
                .thumbnailUrl("https://cdn.lumintra.com/products/bundle.png")
                .galleryUrls(List.of("https://cdn.lumintra.com/products/b1.png"))
                .tags(List.of(ProductDetailResponse.TagItem.builder()
                        .tagId(1L)
                        .name("combo")
                        .description("Value combo")
                        .build()))
                .ledDetails(ProductDetailResponse.LedDetail.builder()
                        .modelId(5L)
                        .modelName("Matrix 64x64")
                        .specifications(Map.of("dimensions", "64x64", "voltage", "5V"))
                        .stockQuantity(12L)
                        .build())
                .bundleDetails(ProductDetailResponse.BundleDetail.builder()
                        .bundleId(3L)
                        .bundleName("Starter Neon Combo")
                        .description("Bundle desc")
                        .originalPrice(new BigDecimal("550000.00"))
                        .bundlePrice(new BigDecimal("450000.00"))
                        .startAt(Instant.parse("2026-10-01T00:00:00Z"))
                        .endAt(Instant.parse("2026-12-31T23:59:59Z"))
                        .items(List.of(ProductDetailResponse.BundleItemDetail.builder()
                                .itemType(BundleItemType.CHARACTER)
                                .versionId(21L)
                                .characterId(10L)
                                .characterName("Cyber Dragon")
                                .versionNumber(2)
                                .previewGifUrl("https://cdn.lumintra.com/characters/dragon.gif")
                                .quantity(1)
                                .build()))
                        .build())
                .subscriptionDetails(ProductDetailResponse.SubscriptionDetail.builder()
                        .subscriptionDetailId(7L)
                        .planCode("VIP_ANNUAL")
                        .planName("VIP Pass")
                        .isLifetime(false)
                        .durationDays(365)
                        .allAccessCharacters(true)
                        .maxCommissionRevisions(3)
                        .maxMobileProfiles(10)
                        .features(Map.of(
                                "commissionPriorityQueue", true,
                                "commissionDiscountPercent", 10,
                                "mobileCloudBackup", true
                        ))
                        .perksDescription("All catalog animations, 3 commission revisions, 10 mobile profiles")
                        .build())
                .createdAt(Instant.parse("2026-10-03T10:00:00Z"))
                .build();

        byte[] serialized = serializer.serialize(original);
        Object deserialized = serializer.deserialize(serialized);

        assertThat(deserialized).isInstanceOf(ProductDetailResponse.class);
        assertThat(deserialized).isEqualTo(original);
    }

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
        public BundleRepository bundleRepository() {
            return mock(BundleRepository.class);
        }

        @Bean
        public BundleItemRepository bundleItemRepository() {
            return mock(BundleItemRepository.class);
        }

        @Bean
        public SubscriptionDetailRepository subscriptionDetailRepository() {
            return mock(SubscriptionDetailRepository.class);
        }

        @Bean
        public LedModelLookupService ledModelLookupService() {
            return mock(LedModelLookupService.class);
        }

        @Bean
        public CharacterLookupService characterLookupService() {
            return mock(CharacterLookupService.class);
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
                                throw new RuntimeException("Redis connection refused (Simulated Outage)");
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
