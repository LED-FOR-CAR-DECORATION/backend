package com.lumintra.backend.store;

import com.lumintra.backend.character.domain.service.CharacterCreationService;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelCreationService;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
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
    private EmployeeLookupService employeeLookupService;

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
    @DisplayName("N: Clean database seeds 12 tags, 3 LED models, 10 characters, bundle, subscription, and evicts store cache")
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

        given(ledModelLookupService.findByProductId(anyLong())).willReturn(Optional.empty());
        given(ledModelCreationService.createForProduct(anyLong(), anyString(), anyMap(), anyLong()))
                .willAnswer(inv -> inv.getArgument(0));

        given(employeeLookupService.findActiveDesigners()).willReturn(
                List.of(new EmployeeProfileSnapshot(77L, "Nhà thiết kế", "designer@lumintra.com")));

        AtomicLong versionIds = new AtomicLong(501L);
        given(characterLookupService.findVersionIdByProductId(anyLong())).willReturn(Optional.empty());
        given(characterCreationService.createForProduct(
                anyLong(), any(), anyString(), anyString(), anyInt(),
                anyString(), anyString(), any(), any(), any()))
                .willAnswer(inv -> versionIds.getAndIncrement());

        given(bundleRepository.findByProductId(anyLong())).willReturn(Optional.empty());
        given(bundleRepository.save(any(BundleEntity.class))).willAnswer(inv -> {
            BundleEntity bundle = inv.getArgument(0);
            ReflectionTestUtils.setField(bundle, "bundleId", 900L);
            return bundle;
        });

        given(subscriptionDetailRepository.findByProductId(anyLong())).willReturn(Optional.empty());
        given(subscriptionDetailRepository.findByPlanCode(anyString()))
                .willReturn(Optional.empty());

        seeder.run(applicationArguments);

        verify(tagRepository, times(12)).save(any(TagEntity.class));

        verify(productRepository, times(19)).save(productCaptor.capture());
        List<ProductEntity> savedProducts = productCaptor.getAllValues();
        assertThat(savedProducts)
                .extracting(ProductEntity::getProductName)
                .containsExactly(
                        StoreDataSeeder.PRODUCT_LED_PRO,
                        StoreDataSeeder.PRODUCT_LED_COMPACT,
                        StoreDataSeeder.PRODUCT_LED_MINI,
                        StoreDataSeeder.PRODUCT_CHAR_01,
                        StoreDataSeeder.PRODUCT_CHAR_02,
                        StoreDataSeeder.PRODUCT_CHAR_03,
                        StoreDataSeeder.PRODUCT_CHAR_04,
                        StoreDataSeeder.PRODUCT_CHAR_05,
                        StoreDataSeeder.PRODUCT_CHAR_06,
                        StoreDataSeeder.PRODUCT_CHAR_07,
                        StoreDataSeeder.PRODUCT_CHAR_08,
                        StoreDataSeeder.PRODUCT_CHAR_09,
                        StoreDataSeeder.PRODUCT_CHAR_10,
                        StoreDataSeeder.PRODUCT_BUNDLE_STARTER,
                        StoreDataSeeder.PRODUCT_BUNDLE_ANIME_TRIO,
                        StoreDataSeeder.PRODUCT_SUB_VIP,
                        StoreDataSeeder.PRODUCT_SUB_STANDARD_30D,
                        StoreDataSeeder.PRODUCT_SUB_PRO_30D,
                        StoreDataSeeder.PRODUCT_SUB_ULTIMATE_LIFETIME
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
                        ProductType.CHARACTER,
                        ProductType.CHARACTER,
                        ProductType.CHARACTER,
                        ProductType.CHARACTER,
                        ProductType.CHARACTER,
                        ProductType.CHARACTER,
                        ProductType.CHARACTER,
                        ProductType.BUNDLE,
                        ProductType.BUNDLE,
                        ProductType.SUBSCRIPTION,
                        ProductType.SUBSCRIPTION,
                        ProductType.SUBSCRIPTION,
                        ProductType.SUBSCRIPTION
                );
        assertThat(savedProducts)
                .extracting(ProductEntity::getStatus)
                .containsExactly(
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED,
                        ProductStatus.PUBLISHED
                );

        // LED models linked to the 3 seeded LED products (101L, 102L, 103L)
        verify(ledModelCreationService).createForProduct(eq(101L), eq(StoreDataSeeder.PRODUCT_LED_PRO), anyMap(), eq(10L));
        verify(ledModelCreationService).createForProduct(eq(102L), eq(StoreDataSeeder.PRODUCT_LED_COMPACT), anyMap(), eq(10L));
        verify(ledModelCreationService).createForProduct(eq(103L), eq(StoreDataSeeder.PRODUCT_LED_MINI), anyMap(), eq(10L));

        // Character versions linked sequentially to products 104L..113L and assigned to sample designer
        for (int i = 0; i < StoreDataSeeder.CHARACTER_SEEDS.size(); i++) {
            StoreDataSeeder.CharacterSeedSpec spec = StoreDataSeeder.CHARACTER_SEEDS.get(i);
            verify(characterCreationService).createForProduct(
                    eq(104L + i),
                    eq(77L),
                    eq(spec.productName()),
                    eq(spec.description()),
                    eq(1),
                    eq(spec.previewUrl()),
                    eq(spec.ledBinaryUrl()),
                    eq(spec.frameCount()),
                    eq(spec.frameDelayMs()),
                    eq(spec.byteSize()));
        }

        // Bundles: Starter Combo (114L: LED 64x32 Compact 102L + Heo Hong 501L + Capybara 502L) and Anime Chibi Trio Combo (115L: Nezuko 505L, Maomao 509L, Sakura Miku 510L)
        verify(bundleRepository, times(2)).save(bundleCaptor.capture());
        assertThat(bundleCaptor.getAllValues())
                .extracting(BundleEntity::getProductId)
                .containsExactly(114L, 115L);
        verify(bundleItemRepository, times(6)).save(bundleItemCaptor.capture());
        assertThat(bundleItemCaptor.getAllValues())
                .extracting(
                        BundleItemEntity::getItemType,
                        BundleItemEntity::getModelId,
                        BundleItemEntity::getVersionId,
                        BundleItemEntity::getQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(BundleItemType.LED, 102L, null, 1),
                        org.assertj.core.groups.Tuple.tuple(BundleItemType.CHARACTER, null, 501L, 1),
                        org.assertj.core.groups.Tuple.tuple(BundleItemType.CHARACTER, null, 502L, 1),
                        org.assertj.core.groups.Tuple.tuple(BundleItemType.CHARACTER, null, 505L, 1),
                        org.assertj.core.groups.Tuple.tuple(BundleItemType.CHARACTER, null, 509L, 1),
                        org.assertj.core.groups.Tuple.tuple(BundleItemType.CHARACTER, null, 510L, 1)
                );

        // Subscription details link the 4 seeded subscription plans (116L..119L)
        verify(subscriptionDetailRepository, times(4)).save(subscriptionCaptor.capture());
        assertThat(subscriptionCaptor.getAllValues())
                .extracting(
                        SubscriptionDetailEntity::getProductId,
                        SubscriptionDetailEntity::getPlanCode,
                        SubscriptionDetailEntity::isLifetime,
                        SubscriptionDetailEntity::getDurationDays,
                        SubscriptionDetailEntity::isAllAccessCharacters,
                        SubscriptionDetailEntity::getMaxCommissionRevisions,
                        SubscriptionDetailEntity::getMaxMobileProfiles)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                116L, StoreDataSeeder.SUBSCRIPTION_PLAN_CODE_VIP, true, null, true, 0, 3),
                        org.assertj.core.groups.Tuple.tuple(
                                117L, StoreDataSeeder.SUBSCRIPTION_PLAN_CODE_STANDARD_30D, false, 30, false, 3, 5),
                        org.assertj.core.groups.Tuple.tuple(
                                118L, StoreDataSeeder.SUBSCRIPTION_PLAN_CODE_PRO_30D, false, 30, false, 5, 10),
                        org.assertj.core.groups.Tuple.tuple(
                                119L, StoreDataSeeder.SUBSCRIPTION_PLAN_CODE_ULTIMATE_LIFETIME, true, null, true, 10, 20)
                );
        assertThat(savedProducts.get(15).getBasePrice()).isEqualByComparingTo("999999.00");
        assertThat(savedProducts.get(16).getBasePrice()).isEqualByComparingTo("399999.00");
        assertThat(savedProducts.get(17).getBasePrice()).isEqualByComparingTo("599999.00");
        assertThat(savedProducts.get(18).getBasePrice()).isEqualByComparingTo("4999999.00");

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
        given(subscriptionDetailRepository.findByProductId(116L)).willReturn(Optional.empty());
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
        stubExistingProduct(StoreDataSeeder.PRODUCT_LED_MINI, 103L, ProductType.LED, ProductStatus.PUBLISHED, "145000.00");

        for (int i = 0; i < StoreDataSeeder.CHARACTER_SEEDS.size(); i++) {
            StoreDataSeeder.CharacterSeedSpec spec = StoreDataSeeder.CHARACTER_SEEDS.get(i);
            long prodId = 104L + i;
            stubExistingProduct(spec.productName(), prodId, ProductType.CHARACTER, spec.status(), spec.basePrice().toPlainString());
            given(characterLookupService.findVersionIdByProductId(prodId)).willReturn(Optional.of(501L + i));
        }

        stubExistingProduct(StoreDataSeeder.PRODUCT_BUNDLE_STARTER, 114L, ProductType.BUNDLE, ProductStatus.PUBLISHED, "169000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_BUNDLE_ANIME_TRIO, 115L, ProductType.BUNDLE, ProductStatus.PUBLISHED, "129000.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_SUB_VIP, 116L, ProductType.SUBSCRIPTION, ProductStatus.PUBLISHED, "999999.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_SUB_STANDARD_30D, 117L, ProductType.SUBSCRIPTION, ProductStatus.PUBLISHED, "399999.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_SUB_PRO_30D, 118L, ProductType.SUBSCRIPTION, ProductStatus.PUBLISHED, "599999.00");
        stubExistingProduct(StoreDataSeeder.PRODUCT_SUB_ULTIMATE_LIFETIME, 119L, ProductType.SUBSCRIPTION, ProductStatus.PUBLISHED, "4999999.00");

        given(ledModelLookupService.findByProductId(101L))
                .willReturn(Optional.of(new LedModelSnapshot(101L, StoreDataSeeder.PRODUCT_LED_PRO, Map.of(), 50L)));
        given(ledModelLookupService.findByProductId(102L))
                .willReturn(Optional.of(new LedModelSnapshot(102L, StoreDataSeeder.PRODUCT_LED_COMPACT, Map.of(), 30L)));
        given(ledModelLookupService.findByProductId(103L))
                .willReturn(Optional.of(new LedModelSnapshot(103L, StoreDataSeeder.PRODUCT_LED_MINI, Map.of(), 40L)));

        BundleEntity existingStarterBundle = BundleEntity.create(
                114L, StoreDataSeeder.PRODUCT_BUNDLE_STARTER, "desc",
                new BigDecimal("175000.00"), new BigDecimal("159000.00"),
                "PUBLISHED", Instant.now(), null);
        ReflectionTestUtils.setField(existingStarterBundle, "bundleId", 900L);
        given(bundleRepository.findByProductId(114L)).willReturn(Optional.of(existingStarterBundle));

        BundleEntity existingAnimeBundle = BundleEntity.create(
                115L, StoreDataSeeder.PRODUCT_BUNDLE_ANIME_TRIO, "desc",
                new BigDecimal("154000.00"), new BigDecimal("129000.00"),
                "PUBLISHED", Instant.now(), null);
        ReflectionTestUtils.setField(existingAnimeBundle, "bundleId", 901L);
        given(bundleRepository.findByProductId(115L)).willReturn(Optional.of(existingAnimeBundle));

        given(subscriptionDetailRepository.findByProductId(116L))
                .willReturn(Optional.of(mock(SubscriptionDetailEntity.class)));
        given(subscriptionDetailRepository.findByProductId(117L))
                .willReturn(Optional.of(mock(SubscriptionDetailEntity.class)));
        given(subscriptionDetailRepository.findByProductId(118L))
                .willReturn(Optional.of(mock(SubscriptionDetailEntity.class)));
        given(subscriptionDetailRepository.findByProductId(119L))
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
