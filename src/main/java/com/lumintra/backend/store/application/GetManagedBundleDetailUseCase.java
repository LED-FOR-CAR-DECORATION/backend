package com.lumintra.backend.store.application;

import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.shared.exception.store.BundleNotFoundException;
import com.lumintra.backend.store.api.response.ManagedBundleDetailResponse;
import com.lumintra.backend.store.api.response.ProductDetailResponse;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetManagedBundleDetailUseCase {

    private final BundleRepository bundleRepository;
    private final BundleItemRepository bundleItemRepository;
    private final ProductRepository productRepository;
    private final LedModelLookupService ledModelLookupService;
    private final CharacterLookupService characterLookupService;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public ManagedBundleDetailResponse execute(Long id) {
        if (id == null || id <= 0) {
            throw new BundleNotFoundException();
        }

        BundleEntity bundle = bundleRepository.findById(id)
                .or(() -> bundleRepository.findByProductId(id))
                .orElseThrow(BundleNotFoundException::new);

        ProductEntity bundleProduct = productRepository.findById(bundle.getProductId()).orElse(null);
        List<BundleItemEntity> bundleItems =
                bundleItemRepository.findAllByBundle_BundleIdOrderByBundleItemIdAsc(bundle.getBundleId());

        Set<Long> modelIds = bundleItems.stream()
                .filter(item -> item.getItemType() == BundleItemType.LED && item.getModelId() != null)
                .map(BundleItemEntity::getModelId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> versionIds = bundleItems.stream()
                .filter(item -> item.getItemType() == BundleItemType.CHARACTER && item.getVersionId() != null)
                .map(BundleItemEntity::getVersionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, LedModelSnapshot> ledModels = modelIds.isEmpty()
                ? Collections.emptyMap()
                : ledModelLookupService.findByModelIds(modelIds);

        Map<Long, CharacterVersionSnapshot> characterVersions = versionIds.isEmpty()
                ? Collections.emptyMap()
                : characterLookupService.findDetailsByVersionIds(versionIds);

        Set<Long> componentProductIds = new LinkedHashSet<>();
        ledModels.values().stream()
                .map(LedModelSnapshot::productId)
                .filter(Objects::nonNull)
                .forEach(componentProductIds::add);
        characterVersions.values().stream()
                .map(CharacterVersionSnapshot::productId)
                .filter(Objects::nonNull)
                .forEach(componentProductIds::add);

        Map<Long, ProductEntity> componentProducts = componentProductIds.isEmpty()
                ? Collections.emptyMap()
                : productRepository.findAllById(componentProductIds).stream()
                        .collect(Collectors.toMap(
                                ProductEntity::getProductId,
                                Function.identity(),
                                (existing, replacement) -> existing
                        ));

        List<ProductDetailResponse.BundleItemDetail> resolvedItems = bundleItems.stream()
                .map(item -> productMapper.toBundleItemDetail(item, ledModels, characterVersions, componentProducts))
                .toList();

        List<String> galleryUrls = bundleProduct != null && bundleProduct.getGalleryUrls() != null
                ? List.copyOf(bundleProduct.getGalleryUrls())
                : Collections.emptyList();

        List<Long> tagIds = bundleProduct != null && bundleProduct.getTags() != null
                ? bundleProduct.getTags().stream()
                        .map(TagEntity::getTagId)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList()
                : Collections.emptyList();

        return ManagedBundleDetailResponse.builder()
                .bundleId(bundle.getBundleId())
                .productId(bundle.getProductId())
                .bundleName(bundle.getBundleName())
                .description(bundle.getDescription())
                .thumbnailUrl(bundleProduct != null ? bundleProduct.getThumbnailUrl() : null)
                .galleryUrls(galleryUrls)
                .originalPrice(bundle.getOriginalPrice())
                .bundlePrice(bundle.getBundlePrice())
                .status(bundle.getStatus())
                .tagIds(tagIds)
                .items(resolvedItems)
                .build();
    }
}
