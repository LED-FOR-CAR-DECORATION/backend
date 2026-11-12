package com.lumintra.backend.store.infrastructure.persistence.mapper;

import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.api.response.ProductDetailResponse;
import com.lumintra.backend.store.api.response.ProductListResponse;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProductMapper {

    public ProductListResponse toProductListResponse(Page<ProductEntity> page) {
        if (page == null) {
            return new ProductListResponse(Collections.emptyList(), null);
        }
        List<ProductListResponse.ProductItem> items = toProductItemList(page.getContent());
        PaginationMeta meta = PaginationMeta.from(page);
        return new ProductListResponse(items, meta);
    }

    public ProductListResponse.ProductItem toProductItem(ProductEntity entity) {
        if (entity == null) {
            return null;
        }

        return ProductListResponse.ProductItem.builder()
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .description(entity.getDescription())
                .productType(entity.getProductType())
                .basePrice(entity.getBasePrice())
                .thumbnailUrl(entity.getThumbnailUrl())
                .galleryUrls(entity.getGalleryUrls() != null ? entity.getGalleryUrls() : Collections.emptyList())
                .tags(toTagItemList(entity.getTags()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public List<ProductListResponse.ProductItem> toProductItemList(List<ProductEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::toProductItem)
                .toList();
    }

    public ProductListResponse.ProductItem.TagItem toTagItem(TagEntity entity) {
        if (entity == null) {
            return null;
        }
        return ProductListResponse.ProductItem.TagItem.builder()
                .tagId(entity.getTagId())
                .name(entity.getName())
                .description(entity.getDescription())
                .build();
    }

    public List<ProductListResponse.ProductItem.TagItem> toTagItemList(Set<TagEntity> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags.stream()
                .map(this::toTagItem)
                .toList();
    }

    public ProductDetailResponse toProductDetailResponse(
            ProductEntity entity,
            LedModelSnapshot ledModel,
            CharacterVersionSnapshot characterVersion,
            BundleEntity bundle,
            List<BundleItemEntity> bundleItems,
            Map<Long, LedModelSnapshot> bundleLedModels,
            Map<Long, CharacterVersionSnapshot> bundleCharacterVersions,
            SubscriptionDetailEntity subscriptionDetail) {
        if (entity == null) {
            return null;
        }

        ProductType type = entity.getProductType();

        return ProductDetailResponse.builder()
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .description(entity.getDescription())
                .productType(type)
                .basePrice(entity.getBasePrice())
                .status(entity.getStatus())
                .thumbnailUrl(entity.getThumbnailUrl())
                .galleryUrls(entity.getGalleryUrls() != null ? List.copyOf(entity.getGalleryUrls()) : Collections.emptyList())
                .tags(toDetailTagItems(entity.getTags()))
                .ledDetails(type == ProductType.LED ? toLedDetail(ledModel) : null)
                .characterDetails(type == ProductType.CHARACTER ? toCharacterDetail(characterVersion) : null)
                .bundleDetails(type == ProductType.BUNDLE
                        ? toBundleDetail(bundle, bundleItems, bundleLedModels, bundleCharacterVersions)
                        : null)
                .subscriptionDetails(type == ProductType.SUBSCRIPTION
                        ? toSubscriptionDetail(subscriptionDetail)
                        : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private List<ProductDetailResponse.TagItem> toDetailTagItems(Set<TagEntity> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags.stream()
                .sorted(Comparator.comparing(TagEntity::getTagId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(tag -> ProductDetailResponse.TagItem.builder()
                        .tagId(tag.getTagId())
                        .name(tag.getName())
                        .description(tag.getDescription())
                        .build())
                .toList();
    }

    private ProductDetailResponse.LedDetail toLedDetail(LedModelSnapshot ledModel) {
        if (ledModel == null) {
            return null;
        }
        Map<String, Object> specifications = ledModel.spec() != null
                ? new LinkedHashMap<>(ledModel.spec())
                : Collections.emptyMap();
        return ProductDetailResponse.LedDetail.builder()
                .modelId(ledModel.modelId())
                .modelName(ledModel.modelName())
                .specifications(specifications)
                .stockQuantity(ledModel.stockQuantity())
                .build();
    }

    private ProductDetailResponse.CharacterDetail toCharacterDetail(CharacterVersionSnapshot characterVersion) {
        if (characterVersion == null) {
            return null;
        }
        return ProductDetailResponse.CharacterDetail.builder()
                .characterId(characterVersion.characterId())
                .characterName(characterVersion.characterName())
                .description(characterVersion.description())
                .versionNumber(characterVersion.versionNumber())
                .previewGifUrl(characterVersion.previewGifUrl())
                .frameCount(characterVersion.frameCount())
                .frameDelayMs(characterVersion.frameDelayMs())
                .build();
    }

    private ProductDetailResponse.BundleDetail toBundleDetail(
            BundleEntity bundle,
            List<BundleItemEntity> bundleItems,
            Map<Long, LedModelSnapshot> bundleLedModels,
            Map<Long, CharacterVersionSnapshot> bundleCharacterVersions) {
        if (bundle == null) {
            return null;
        }

        Map<Long, LedModelSnapshot> safeLedModels = bundleLedModels != null ? bundleLedModels : Collections.emptyMap();
        Map<Long, CharacterVersionSnapshot> safeCharacters =
                bundleCharacterVersions != null ? bundleCharacterVersions : Collections.emptyMap();

        List<ProductDetailResponse.BundleItemDetail> items = bundleItems == null
                ? Collections.emptyList()
                : bundleItems.stream()
                        .map(item -> toBundleItemDetail(item, safeLedModels, safeCharacters))
                        .toList();

        return ProductDetailResponse.BundleDetail.builder()
                .bundleId(bundle.getBundleId())
                .bundleName(bundle.getBundleName())
                .description(bundle.getDescription())
                .originalPrice(bundle.getOriginalPrice())
                .bundlePrice(bundle.getBundlePrice())
                .startAt(bundle.getStartAt())
                .endAt(bundle.getEndAt())
                .items(items)
                .build();
    }

    private ProductDetailResponse.BundleItemDetail toBundleItemDetail(
            BundleItemEntity item,
            Map<Long, LedModelSnapshot> bundleLedModels,
            Map<Long, CharacterVersionSnapshot> bundleCharacterVersions) {
        if (item.getItemType() == BundleItemType.LED) {
            LedModelSnapshot led = item.getModelId() != null ? bundleLedModels.get(item.getModelId()) : null;
            return ProductDetailResponse.BundleItemDetail.builder()
                    .itemType(BundleItemType.LED)
                    .modelId(item.getModelId())
                    .modelName(led != null ? led.modelName() : null)
                    .stockQuantity(led != null ? led.stockQuantity() : null)
                    .quantity(item.getQuantity())
                    .build();
        }

        CharacterVersionSnapshot character = item.getVersionId() != null
                ? bundleCharacterVersions.get(item.getVersionId())
                : null;
        return ProductDetailResponse.BundleItemDetail.builder()
                .itemType(BundleItemType.CHARACTER)
                .versionId(item.getVersionId())
                .characterId(character != null ? character.characterId() : null)
                .characterName(character != null ? character.characterName() : null)
                .versionNumber(character != null ? character.versionNumber() : null)
                .previewGifUrl(character != null ? character.previewGifUrl() : null)
                .quantity(item.getQuantity())
                .build();
    }

    private ProductDetailResponse.SubscriptionDetail toSubscriptionDetail(
            SubscriptionDetailEntity subscriptionDetail) {
        if (subscriptionDetail == null) {
            return null;
        }
        Map<String, Object> features = subscriptionDetail.getFeatures() != null
                ? new LinkedHashMap<>(subscriptionDetail.getFeatures())
                : Collections.emptyMap();
        return ProductDetailResponse.SubscriptionDetail.builder()
                .subscriptionDetailId(subscriptionDetail.getSubscriptionDetailId())
                .planCode(subscriptionDetail.getPlanCode())
                .planName(subscriptionDetail.getPlanName())
                .isLifetime(subscriptionDetail.isLifetime())
                .durationDays(subscriptionDetail.getDurationDays())
                .allAccessCharacters(subscriptionDetail.isAllAccessCharacters())
                .maxCommissionRevisions(subscriptionDetail.getMaxCommissionRevisions())
                .maxMobileProfiles(subscriptionDetail.getMaxMobileProfiles())
                .features(features)
                .perksDescription(subscriptionDetail.getPerksDescription())
                .build();
    }
}
