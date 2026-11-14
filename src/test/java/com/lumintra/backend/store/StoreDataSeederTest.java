package com.lumintra.backend.store;

import com.lumintra.backend.character.domain.service.CharacterCreationService;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelCreationService;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.bootstrap.StoreDataSeeder;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StoreDataSeederTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BundleRepository bundleRepository;

    @Mock
    private BundleItemRepository bundleItemRepository;

    @Mock
    private SubscriptionDetailRepository subscriptionDetailRepository;

    @Mock
    private LedModelCreationService ledModelCreationService;

    @Mock
    private LedModelLookupService ledModelLookupService;

    @Mock
    private CharacterCreationService characterCreationService;

    @Mock
    private CharacterLookupService characterLookupService;

    @Mock
    private StoreCacheEvictor storeCacheEvictor;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private StoreDataSeeder seeder;

    @Captor
    private ArgumentCaptor<ProductEntity> productCaptor;

    @Captor
    private ArgumentCaptor<BundleEntity> bundleCaptor;

    @Captor
    private ArgumentCaptor<BundleItemEntity> bundleItemCaptor;

    @Captor
    private ArgumentCaptor<SubscriptionDetailEntity> subscriptionCaptor;

    @Test
    @DisplayName("N: Clean database seeds tags, LED models, characters, bundle, subscription, and evicts store cache")
    void run_cleanDatabase_seedsAllCatalogEntitiesAndEvictsCache() {
        AtomicLong tagIds = new AtomicLong(1L);
        given(tagRepository.findByName(anyString())).willReturn(Optional.empty());
        given(tagRepository.save(any(TagEntity.class))).willAnswer(inv -> {
            TagEntity tag = inv.getArgument(0);
            ReflectionTestUtils.setField(tag, "tagId", tagIds.getAndIncrement());
            return tag;
        });

        AtomicLong productIds = new AtomicLong(101L);
        given(productRepository.findFirstByProductName(anyString())).willReturn(Optional.empty());
        given(productRepository.save(any(ProductEntity.class))).willAnswer(inv -> {
            ProductEntity product = inv.getArgument(0);
            ReflectionTestUtils.setField(product, "productId", productIds.getAndIncrement());
            return product;
        });

        given(ledModelLookupService.findByModelId(anyLong())).willReturn(Optional.empty());

        AtomicLong versionIds = new AtomicLong(501L);
        given(characterLookupService.findVersionIdByProductId(anyLong())).willReturn(Optional.empty());
        given(characterCreationService.createForProduct(
                anyLong(), anyString(), anyString(), anyInt(),
                anyString(), anyString(), any(), any(), any()))
                .willAnswer(inv -> versionIds.getAndIncrement());

        given(bundleRepository.findByProductId(anyLong())).willReturn(Optional.empty());
        given(bundleRepository.save(any(BundleEntity.class))).willAnswer(inv -> {
            BundleEntity bundle = inv.getArgument(0);
            ReflectionTestUtils.setField(bundle, "bundleId", 900L);
            return bundle;
        });

        given(subscriptionDetailRepository.findByProductId(anyLong())).willReturn(Optional.empty());
        given(subscriptionDetailRepository.findByPlanCode(StoreDataSeeder.SUBSCRIPTION_PLAN_CODE_VIP))
                .willReturn(Optional.empty());

        seeder.run(applicationArguments);

        verify(tagRepository, times(4)).save(any(TagEntity.class));

        verify(productRepository, times(8)).save(productCaptor.capture());
        List<ProductEntity> savedProducts = productCaptor.getAllValues();
        assertThat(savedProducts)
                .extracting(ProductEntity::getProductName)
                .containsExactly(
                        StoreDataSeeder.PRODUCT_LED_PRO,
                        StoreDataSeeder.PRODUCT_LED_COMPACT,
                        StoreDataSeeder.PRODUCT_LED_PROTOTYPE,
                        StoreDataSeeder.PRODUCT_CHAR_CAT,
                        StoreDataSeeder.PRODUCT_CHAR_DRAGON,
                        StoreDataSeeder.PRODUCT_CHAR_MASCOT,
                        StoreDataSeeder.PRODUCT_BUNDLE_STARTER,
                        StoreDataSeeder.PRODUCT_SUB_VIP
                );
        assertThat(savedProducts)
                .extracting(ProductEntity::getProductType)
                .containsExactly(
                        ProductType.LED,
                        ProductType.LED,
                        ProductType.LED,
                        ProductType.CHARACTER,
                        ProductType.CHARACTER,
                        ProductType.CHARACTER,
                        ProductType.BUNDLE,
                        ProductType.SUBSCRIPTION
                );
        assertThat(savedProducts)
                .extracting(ProductEntity::getStatus)
                .containsExactly(
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.UNPUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.ARCHIVED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED
                );

        // LED models linked to the 3 seeded LED products (101L, 102L, 103L)
        verify(ledModelCreationService).createForProduct(eq(101L), eq(StoreDataSeeder.PRODUCT_LED_PRO), anyMap(), eq(50L));
        verify(ledModelCreationService).createForProduct(eq(102L), eq(StoreDataSeeder.PRODUCT_LED_COMPACT), anyMap(), eq(30L));
        verify(ledModelCreationService).createForProduct(eq(103L), eq(StoreDataSeeder.PRODUCT_LED_PROTOTYPE), anyMap(), eq(5L));

        // Character versions linked to the 3 seeded CHARACTER products (104L, 105L, 106L)
        verify(characterCreationService).createForProduct(
                eq(104L), eq(StoreDataSeeder.PRODUCT_CHAR_CAT), anyString(), eq(1),
                anyString(), anyString(), eq(24), eq(80), eq(196608));
        verify(characterCreationService).createForProduct(
                eq(105L), eq(StoreDataSeeder.PRODUCT_CHAR_DRAGON), anyString(), eq(1),
                anyString(), anyString(), eq(30), eq(70), eq(245760));
        verify(characterCreationService).createForProduct(
                eq(106L), eq(StoreDataSeeder.PRODUCT_CHAR_MASCOT), anyString(), eq(1),
                anyString(), anyString(), eq(16), eq(100), eq(131072));

        // Bundle links LED Pro (101L) and Cyber Dragon versionId (502L)
        verify(bundleRepository).save(bundleCaptor.capture());
        assertThat(bundleCaptor.getValue().getProductId()).isEqualTo(107L);
        verify(bundleItemRepository, times(2)).save(bundleItemCaptor.capture());
        assertThat(bundleItemCaptor.getAllValues())
                .extracting(
                        BundleItemEntity::getItemType,
                        BundleItemEntity::getModelId,
                        BundleItemEntity::getVersionId,
                        BundleItemEntity::getQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(BundleItemType.LED, 101L, null, 1),
                        org.assertj.core.groups.Tuple.tuple(BundleItemType.CHARACTER, null, 502L, 1)
                );

        // Subscription detail links VIP product (108L)
        verify(subscriptionDetailRepository).save(subscriptionCaptor.capture());
        SubscriptionDetailEntity savedSub = subscriptionCaptor.getValue();
        assertThat(savedSub.getProductId()).isEqualTo(108L);
        assertThat(savedSub.getPlanCode()).isEqualTo(StoreDataSeeder.SUBSCRIPTION_PLAN_CODE_VIP);

        verify(storeCacheEvictor, times(1)).evictAllProducts();
    }

    @Test
    @DisplayName("N: Re-running when all catalog records already exist skips all writes and cache eviction")
    void run_existingCatalogData_skipsAllWritesAndCacheEviction() {
        stubExistingCatalog();

        seeder.run(applicationArguments);

        verify(tagRepository, never()).save(any());
        verify(productRepository, never()).save(any());
        verifyNoInteractions(ledModelCreationService);
        verifyNoInteractions(characterCreationService);
        verify(bundleRepository, never()).save(any());
        verifyNoInteractions(bundleItemRepository);
        verify(subscriptionDetailRepository, never()).save(any());
        verifyNoInteractions(storeCacheEvictor);
    }

    @Test
    @DisplayName("B: Existing subscription planCode on another row prevents duplicate subscription detail insertion")
    void run_existingPlanCode_skipsDuplicateSubscriptionDetail() {
        stubExistingCatalog();
        given(subscriptionDetailRepository.findByProductId(108L)).willReturn(Optional.empty());
        given(subscriptionDetailRepository.findByPlanCode(StoreDataSeeder.SUBSCRIPTION_PLAN_CODE_VIP))
                .willReturn(Optional.of(mock(SubscriptionDetailEntity.class)));

        seeder.run(applicationArguments);

        verify(subscriptionDetailRepository, never()).save(any());
        verifyNoInteractions(storeCacheEvictor);
    }

    private void stubExistingCatalog() {
        given(tagRepository.findByName(anyString())).willAnswer(inv -> {
            TagEntity tag = TagEntity.create(inv.getArgument(0), "desc");
            ReflectionTestUtils.setField(tag, "tagId", 1L);
            return Optional.of(tag);
        });

        stubExistingProduct(StoreDataSeeder.PRODUCT_LED_PRO, 101L, ProductType.LED, ProductStatus.PUBLISHED, "250000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_LED_COMPACT, 102L, ProductType.LED, ProductStatus.PUBLISHED, "190000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_LED_PROTOTYPE, 103L, ProductType.LED, ProductStatus.UNPUBLISHED, "299000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_CHAR_CAT, 104L, ProductType.CHARACTER, ProductStatus.PUBLISHED, "50000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_CHAR_DRAGON, 105L, ProductType.CHARACTER, ProductStatus.PUBLISHED, "75000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_CHAR_MASCOT, 106L, ProductType.CHARACTER, ProductStatus.ARCHIVED, "40000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_BUNDLE_STARTER, 107L, ProductType.BUNDLE, ProductStatus.PUBLISHED, "280000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_SUB_VIP, 108L, ProductType.SUBSCRIPTION, ProductStatus.PUBLISHED, "99000.00");

        given(ledModelLookupService.findByModelId(101L))
                .willReturn(Optional.of(new LedModelSnapshot(101L, StoreDataSeeder.PRODUCT_LED_PRO, Map.of(), 50L)));
        given(ledModelLookupService.findByModelId(102L))
                .willReturn(Optional.of(new LedModelSnapshot(102L, StoreDataSeeder.PRODUCT_LED_COMPACT, Map.of(), 30L)));
        given(ledModelLookupService.findByModelId(103L))
                .willReturn(Optional.of(new LedModelSnapshot(103L, StoreDataSeeder.PRODUCT_LED_PROTOTYPE, Map.of(), 5L)));

        given(characterLookupService.findVersionIdByProductId(104L)).willReturn(Optional.of(501L));
        given(characterLookupService.findVersionIdByProductId(105L)).willReturn(Optional.of(502L));
        given(characterLookupService.findVersionIdByProductId(106L)).willReturn(Optional.of(503L));

        BundleEntity existingBundle = BundleEntity.create(
                107L, StoreDataSeeder.PRODUCT_BUNDLE_STARTER, "desc",
                new BigDecimal("325000.00"), new BigDecimal("280000.00"),
                "PUBLISHED", Instant.now(), null);
        ReflectionTestUtils.setField(existingBundle, "bundleId", 900L);
        given(bundleRepository.findByProductId(107L)).willReturn(Optional.of(existingBundle));

        given(subscriptionDetailRepository.findByProductId(108L))
                .willReturn(Optional.of(mock(SubscriptionDetailEntity.class)));
    }

    private void stubExistingProduct(
            String name, Long id, ProductType type, ProductStatus status, String price) {
        ProductEntity product = ProductEntity.create(
                name, type, new BigDecimal(price), "desc", "thumb", List.of(), status, Set.of());
        ReflectionTestUtils.setField(product, "productId", id);
        given(productRepository.findFirstByProductName(name)).willReturn(Optional.of(product));
    }
}
