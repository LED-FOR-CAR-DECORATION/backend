package com.lumintra.backend.store;

import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.store.api.response.ProductDetailResponse;
import com.lumintra.backend.store.application.GetProductDetailUseCase;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProductDetailUseCaseTest {

    private static final Set<ProductStatus> PUBLIC_STATUSES =
            EnumSet.of(ProductStatus.PUBLISHED, ProductStatus.ARCHIVED);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BundleRepository bundleRepository;

    @Mock
    private BundleItemRepository bundleItemRepository;

    @Mock
    private SubscriptionDetailRepository subscriptionDetailRepository;

    @Mock
    private LedModelLookupService ledModelLookupService;

    @Mock
    private CharacterLookupService characterLookupService;

    private GetProductDetailUseCase getProductDetailUseCase;

    @BeforeEach
    void setUp() {
        getProductDetailUseCase = new GetProductDetailUseCase(
                productRepository,
                bundleRepository,
                bundleItemRepository,
                subscriptionDetailRepository,
                ledModelLookupService,
                characterLookupService,
                new ProductMapper()
        );
    }

    @Test
    @DisplayName("UTCID01 [N]: Retrieves ARCHIVED LED product detail with deterministic sorted tags and specifications")
    void execute_archivedLedProduct_returnsLedDetailsAndSortedTags() {
        TagEntity zTag = TagEntity.of(10L, "Windshield", "Car accessory");
        TagEntity aTag = TagEntity.of(2L, "LED Matrix", "Smart display");
        Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");

        ProductEntity entity = ProductEntity.of(
                5L,
                "Den LED Matrix Lumintra v1",
                ProductType.LED,
                new BigDecimal("200000.00"),
                "Compact 64x64 panel",
                "https://cdn.lumintra.com/products/led-v1.png",
                List.of("https://cdn.lumintra.com/products/led-1.png", "https://cdn.lumintra.com/products/led-2.png"),
                ProductStatus.ARCHIVED,
                Set.of(zTag, aTag),
                createdAt
        );

        LedModelSnapshot ledSnapshot = new LedModelSnapshot(
                5L,
                "Matrix 64x64",
                Map.of("dimensions", "64x64", "voltage", "5V"),
                0L
        );

        when(productRepository.findWithTagsByProductIdAndStatusIn(5L, PUBLIC_STATUSES))
                .thenReturn(Optional.of(entity));
        when(ledModelLookupService.findByModelId(5L)).thenReturn(Optional.of(ledSnapshot));

        ProductDetailResponse response = getProductDetailUseCase.execute(5L);

        assertThat(response.productId()).isEqualTo(5L);
        assertThat(response.status()).isEqualTo(ProductStatus.ARCHIVED);
        assertThat(response.galleryUrls()).containsExactly(
                "https://cdn.lumintra.com/products/led-1.png",
                "https://cdn.lumintra.com/products/led-2.png"
        );
        assertThat(response.tags()).extracting(ProductDetailResponse.TagItem::tagId).containsExactly(2L, 10L);
        assertThat(response.ledDetails()).isNotNull();
        assertThat(response.ledDetails().modelId()).isEqualTo(5L);
        assertThat(response.ledDetails().modelName()).isEqualTo("Matrix 64x64");
        assertThat(response.ledDetails().specifications()).containsEntry("dimensions", "64x64").containsEntry("voltage", "5V");
        assertThat(response.ledDetails().stockQuantity()).isZero();
        assertThat(response.characterDetails()).isNull();
        assertThat(response.bundleDetails()).isNull();
        assertThat(response.subscriptionDetails()).isNull();

        verifyNoInteractions(characterLookupService, bundleRepository, bundleItemRepository, subscriptionDetailRepository);
    }

    @Test
    @DisplayName("UTCID02 [N]: Retrieves PUBLISHED CHARACTER product detail via CharacterLookupService without ledBinaryUrl")
    void execute_publishedCharacterProduct_returnsCharacterDetails() {
        Instant createdAt = Instant.parse("2026-10-02T08:00:00Z");
        ProductEntity entity = ProductEntity.of(
                8L,
                "Cyber Dragon",
                ProductType.CHARACTER,
                new BigDecimal("59000.00"),
                "Product description",
                "https://cdn.lumintra.com/products/dragon.png",
                null,
                ProductStatus.PUBLISHED,
                Set.of(),
                createdAt
        );

        CharacterVersionSnapshot snapshot = new CharacterVersionSnapshot(
                21L,
                10L,
                "Cyber Dragon",
                "Neon dragon lore",
                2,
                "https://cdn.lumintra.com/characters/dragon-preview.gif",
                24,
                80
        );

        when(productRepository.findWithTagsByProductIdAndStatusIn(8L, PUBLIC_STATUSES))
                .thenReturn(Optional.of(entity));
        when(characterLookupService.findDetailByProductId(8L)).thenReturn(Optional.of(snapshot));

        ProductDetailResponse response = getProductDetailUseCase.execute(8L);

        assertThat(response.galleryUrls()).isEmpty();
        assertThat(response.characterDetails()).isNotNull();
        assertThat(response.characterDetails().characterId()).isEqualTo(10L);
        assertThat(response.characterDetails().characterName()).isEqualTo("Cyber Dragon");
        assertThat(response.characterDetails().description()).isEqualTo("Neon dragon lore");
        assertThat(response.characterDetails().versionNumber()).isEqualTo(2);
        assertThat(response.characterDetails().previewGifUrl())
                .isEqualTo("https://cdn.lumintra.com/characters/dragon-preview.gif");
        assertThat(response.characterDetails().frameCount()).isEqualTo(24);
        assertThat(response.characterDetails().frameDelayMs()).isEqualTo(80);
        assertThat(response.ledDetails()).isNull();

        verifyNoInteractions(ledModelLookupService, bundleRepository, bundleItemRepository, subscriptionDetailRepository);
    }

    @Test
    @DisplayName("UTCID03 [N]: Retrieves BUNDLE product detail using batch lookups for LED and CHARACTER items")
    void execute_publishedBundleProduct_batchesLookupQueriesWithoutNPlusOne() {
        Instant createdAt = Instant.parse("2026-10-03T10:00:00Z");
        Instant startAt = Instant.parse("2026-10-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-12-31T23:59:59Z");

        ProductEntity product = ProductEntity.of(
                12L,
                "Starter Neon Combo",
                ProductType.BUNDLE,
                new BigDecimal("450000.00"),
                "Bundle summary",
                "https://cdn.lumintra.com/products/bundle.png",
                List.of(),
                ProductStatus.PUBLISHED,
                Set.of(),
                createdAt
        );

        BundleEntity bundle = BundleEntity.create(
                12L,
                "Starter Neon Combo",
                "Hardware + character combo",
                new BigDecimal("550000.00"),
                new BigDecimal("450000.00"),
                "ACTIVE",
                startAt,
                endAt
        );
        bundle.setBundleId(3L);

        BundleItemEntity ledItem = BundleItemEntity.createLedItem(bundle, 5L, 1);
        ledItem.setBundleItemId(101L);
        BundleItemEntity charItem = BundleItemEntity.createCharacterItem(bundle, 21L, 2);
        charItem.setBundleItemId(102L);

        when(productRepository.findWithTagsByProductIdAndStatusIn(12L, PUBLIC_STATUSES))
                .thenReturn(Optional.of(product));
        when(bundleRepository.findByProductId(12L)).thenReturn(Optional.of(bundle));
        when(bundleItemRepository.findAllByBundle_BundleIdOrderByBundleItemIdAsc(3L))
                .thenReturn(List.of(ledItem, charItem));
        when(ledModelLookupService.findByModelIds(Set.of(5L)))
                .thenReturn(Map.of(5L, new LedModelSnapshot(5L, "Matrix 64x64", Map.of(), 15L)));
        when(characterLookupService.findDetailsByVersionIds(Set.of(21L)))
                .thenReturn(Map.of(21L, new CharacterVersionSnapshot(
                        21L, 10L, "Cyber Dragon", "desc", 3,
                        "https://cdn.lumintra.com/characters/dragon.gif", 16, 60)));

        ProductDetailResponse response = getProductDetailUseCase.execute(12L);

        assertThat(response.bundleDetails()).isNotNull();
        assertThat(response.bundleDetails().bundleId()).isEqualTo(3L);
        assertThat(response.bundleDetails().originalPrice()).isEqualByComparingTo("550000.00");
        assertThat(response.bundleDetails().bundlePrice()).isEqualByComparingTo("450000.00");
        assertThat(response.bundleDetails().startAt()).isEqualTo(startAt);
        assertThat(response.bundleDetails().endAt()).isEqualTo(endAt);
        assertThat(response.bundleDetails().items()).hasSize(2);

        ProductDetailResponse.BundleItemDetail first = response.bundleDetails().items().get(0);
        assertThat(first.itemType()).isEqualTo(BundleItemType.LED);
        assertThat(first.modelId()).isEqualTo(5L);
        assertThat(first.modelName()).isEqualTo("Matrix 64x64");
        assertThat(first.stockQuantity()).isEqualTo(15L);
        assertThat(first.quantity()).isEqualTo(1);
        assertThat(first.versionId()).isNull();

        ProductDetailResponse.BundleItemDetail second = response.bundleDetails().items().get(1);
        assertThat(second.itemType()).isEqualTo(BundleItemType.CHARACTER);
        assertThat(second.versionId()).isEqualTo(21L);
        assertThat(second.characterId()).isEqualTo(10L);
        assertThat(second.characterName()).isEqualTo("Cyber Dragon");
        assertThat(second.versionNumber()).isEqualTo(3);
        assertThat(second.previewGifUrl()).isEqualTo("https://cdn.lumintra.com/characters/dragon.gif");
        assertThat(second.quantity()).isEqualTo(2);
        assertThat(second.modelId()).isNull();

        verify(ledModelLookupService).findByModelIds(eq(Set.of(5L)));
        verify(characterLookupService).findDetailsByVersionIds(eq(Set.of(21L)));
    }

    @Test
    @DisplayName("UTCID04 [N]: Retrieves SUBSCRIPTION product detail with store, commission, mobile quotas, and extensible features")
    void execute_subscriptionProduct_loadsFromSubscriptionDetailRepository() {
        ProductEntity entity = ProductEntity.of(
                99L,
                "Lumintra All-Access VIP Pass",
                ProductType.SUBSCRIPTION,
                new BigDecimal("990000.00"),
                "All characters unlocked forever",
                "https://cdn.lumintra.com/products/vip.png",
                List.of(),
                ProductStatus.PUBLISHED,
                Set.of(),
                Instant.parse("2026-10-04T09:00:00Z")
        );

        SubscriptionDetailEntity subscriptionDetail = SubscriptionDetailEntity.of(
                7L,
                99L,
                "VIP_LIFETIME",
                "Lumintra All-Access VIP Pass",
                true,
                null,
                true,
                5,
                10,
                Map.of(
                        "commissionPriorityQueue", true,
                        "commissionDiscountPercent", 15,
                        "mobileCloudBackup", true
                ),
                "Unlimited access to all characters, 5 commission revisions, and 10 mobile saved profiles"
        );

        when(productRepository.findWithTagsByProductIdAndStatusIn(99L, PUBLIC_STATUSES))
                .thenReturn(Optional.of(entity));
        when(subscriptionDetailRepository.findByProductId(99L))
                .thenReturn(Optional.of(subscriptionDetail));

        ProductDetailResponse response = getProductDetailUseCase.execute(99L);

        assertThat(response.subscriptionDetails()).isNotNull();
        assertThat(response.subscriptionDetails().subscriptionDetailId()).isEqualTo(7L);
        assertThat(response.subscriptionDetails().planCode()).isEqualTo("VIP_LIFETIME");
        assertThat(response.subscriptionDetails().planName()).isEqualTo("Lumintra All-Access VIP Pass");
        assertThat(response.subscriptionDetails().isLifetime()).isTrue();
        assertThat(response.subscriptionDetails().durationDays()).isNull();
        assertThat(response.subscriptionDetails().allAccessCharacters()).isTrue();
        assertThat(response.subscriptionDetails().maxCommissionRevisions()).isEqualTo(5);
        assertThat(response.subscriptionDetails().maxMobileProfiles()).isEqualTo(10);
        assertThat(response.subscriptionDetails().features())
                .containsEntry("commissionPriorityQueue", true)
                .containsEntry("commissionDiscountPercent", 15)
                .containsEntry("mobileCloudBackup", true);
        assertThat(response.subscriptionDetails().perksDescription())
                .isEqualTo("Unlimited access to all characters, 5 commission revisions, and 10 mobile saved profiles");

        verify(subscriptionDetailRepository).findByProductId(99L);
        verifyNoInteractions(ledModelLookupService, characterLookupService, bundleRepository, bundleItemRepository);
    }

    @Test
    @DisplayName("UTCID05 [B]: Gracefully handles missing child detail rows and empty bundle item lists")
    void execute_missingChildDetailsOrEmptyBundle_doesNotThrow() {
        ProductEntity ledWithoutModel = ProductEntity.of(
                15L, "LED Orphan", ProductType.LED, BigDecimal.TEN, null, null, null,
                ProductStatus.PUBLISHED, Set.of(), Instant.now());
        when(productRepository.findWithTagsByProductIdAndStatusIn(15L, PUBLIC_STATUSES))
                .thenReturn(Optional.of(ledWithoutModel));
        when(ledModelLookupService.findByModelId(15L)).thenReturn(Optional.empty());

        ProductDetailResponse ledResponse = getProductDetailUseCase.execute(15L);
        assertThat(ledResponse.ledDetails()).isNull();

        ProductEntity bundleWithoutItems = ProductEntity.of(
                16L, "Empty Bundle", ProductType.BUNDLE, BigDecimal.TEN, null, null, null,
                ProductStatus.PUBLISHED, Set.of(), Instant.now());
        BundleEntity bundle = BundleEntity.create(16L, "Empty Bundle", null, BigDecimal.TEN, BigDecimal.TEN, "ACTIVE", null, null);
        bundle.setBundleId(40L);
        when(productRepository.findWithTagsByProductIdAndStatusIn(16L, PUBLIC_STATUSES))
                .thenReturn(Optional.of(bundleWithoutItems));
        when(bundleRepository.findByProductId(16L)).thenReturn(Optional.of(bundle));
        when(bundleItemRepository.findAllByBundle_BundleIdOrderByBundleItemIdAsc(40L)).thenReturn(List.of());

        ProductDetailResponse bundleResponse = getProductDetailUseCase.execute(16L);
        assertThat(bundleResponse.bundleDetails()).isNotNull();
        assertThat(bundleResponse.bundleDetails().items()).isEmpty();

        ProductEntity subscriptionWithoutDetail = ProductEntity.of(
                17L, "Subscription Orphan", ProductType.SUBSCRIPTION, BigDecimal.TEN, null, null, null,
                ProductStatus.PUBLISHED, Set.of(), Instant.now());
        when(productRepository.findWithTagsByProductIdAndStatusIn(17L, PUBLIC_STATUSES))
                .thenReturn(Optional.of(subscriptionWithoutDetail));
        when(subscriptionDetailRepository.findByProductId(17L)).thenReturn(Optional.empty());

        ProductDetailResponse subscriptionResponse = getProductDetailUseCase.execute(17L);
        assertThat(subscriptionResponse.subscriptionDetails()).isNull();

        ProductEntity timedSubscription = ProductEntity.of(
                18L, "Creator Monthly Plan", ProductType.SUBSCRIPTION, new BigDecimal("99000.00"), null, null, null,
                ProductStatus.PUBLISHED, Set.of(), Instant.now());
        SubscriptionDetailEntity nullFeaturesPlan = SubscriptionDetailEntity.of(
                8L, 18L, "CREATOR_MONTHLY", "Creator Monthly", false, 30, false, 2, 10, null, null);
        nullFeaturesPlan.setFeatures(null);
        when(productRepository.findWithTagsByProductIdAndStatusIn(18L, PUBLIC_STATUSES))
                .thenReturn(Optional.of(timedSubscription));
        when(subscriptionDetailRepository.findByProductId(18L)).thenReturn(Optional.of(nullFeaturesPlan));

        ProductDetailResponse timedResponse = getProductDetailUseCase.execute(18L);
        assertThat(timedResponse.subscriptionDetails()).isNotNull();
        assertThat(timedResponse.subscriptionDetails().isLifetime()).isFalse();
        assertThat(timedResponse.subscriptionDetails().durationDays()).isEqualTo(30);
        assertThat(timedResponse.subscriptionDetails().allAccessCharacters()).isFalse();
        assertThat(timedResponse.subscriptionDetails().maxCommissionRevisions()).isEqualTo(2);
        assertThat(timedResponse.subscriptionDetails().maxMobileProfiles()).isEqualTo(10);
        assertThat(timedResponse.subscriptionDetails().features()).isEmpty();

        verify(ledModelLookupService).findByModelId(15L);
        verify(subscriptionDetailRepository).findByProductId(17L);
        verify(subscriptionDetailRepository).findByProductId(18L);
        verifyNoInteractions(characterLookupService);
    }

    @Test
    @DisplayName("UTCID06 [A]: Throws ProductNotFoundException for non-positive IDs, missing products, UNPUBLISHED, and DELETED status")
    void execute_unavailableOrInvalidIds_throwProductNotFoundException() {
        assertThatThrownBy(() -> getProductDetailUseCase.execute(null))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> getProductDetailUseCase.execute(0L))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> getProductDetailUseCase.execute(-5L))
                .isInstanceOf(ProductNotFoundException.class);
        verifyNoInteractions(productRepository);

        when(productRepository.findWithTagsByProductIdAndStatusIn(999L, PUBLIC_STATUSES))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> getProductDetailUseCase.execute(999L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found.");

        // Defense-in-depth check: even if a query returned an UNPUBLISHED/DELETED entity, filter blocks it
        ProductEntity unpublished = ProductEntity.of(
                50L, "Draft LED", ProductType.LED, BigDecimal.TEN, null, null, null,
                ProductStatus.UNPUBLISHED, Set.of(), Instant.now());
        when(productRepository.findWithTagsByProductIdAndStatusIn(eq(50L), any(Collection.class)))
                .thenReturn(Optional.of(unpublished));
        assertThatThrownBy(() -> getProductDetailUseCase.execute(50L))
                .isInstanceOf(ProductNotFoundException.class);

        ProductEntity deleted = ProductEntity.of(
                51L, "Removed LED", ProductType.LED, BigDecimal.TEN, null, null, null,
                ProductStatus.DELETED, Set.of(), Instant.now());
        when(productRepository.findWithTagsByProductIdAndStatusIn(eq(51L), any(Collection.class)))
                .thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> getProductDetailUseCase.execute(51L))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
