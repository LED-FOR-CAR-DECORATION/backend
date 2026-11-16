package com.lumintra.backend.store.application;

import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.shared.exception.store.InvalidBundlePriceException;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.store.api.request.CreateBundleRequest;
import com.lumintra.backend.store.api.request.CreateBundleRequest.Item;
import com.lumintra.backend.store.api.response.CreatedBundleResponse;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.*;
import com.lumintra.backend.store.infrastructure.persistence.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateBundleUseCase {
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private final ProductRepository products;
    private final BundleRepository bundles;
    private final BundleItemRepository bundleItems;
    private final TagRepository tags;
    private final LedModelLookupService models;
    private final CharacterVersionRepository versions;

    @Transactional
    public CreatedBundleResponse execute(CreateBundleRequest request) {
        var ids =
                request.tagIds() == null
                        ? List.<Long>of()
                        : request.tagIds().stream().distinct().toList();
        var selectedTags = tags.findAllById(ids);
        if (selectedTags.size() != ids.size()) {
            throw new StoreProductTagNotFoundException();
        }

        var originalPrice =
                request.items().stream()
                        .map(item -> price(item).multiply(BigDecimal.valueOf(item.quantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.bundlePrice().compareTo(originalPrice) > 0
                || originalPrice.compareTo(MAX_PRICE) > 0) {
            throw new InvalidBundlePriceException(
                    "Bundle price must not exceed original price, and original price must fit the"
                            + " supported range.");
        }

        var product =
                products.save(
                        ProductEntity.create(
                                request.bundleName(),
                                ProductType.BUNDLE,
                                request.bundlePrice(),
                                request.description(),
                                request.thumbnailUrl(),
                                request.galleryUrls(),
                                ProductStatus.UNPUBLISHED,
                                new HashSet<>(selectedTags)));
        var bundle =
                bundles.save(
                        BundleEntity.create(
                                product.getProductId(),
                                request.bundleName(),
                                request.description(),
                                originalPrice,
                                request.bundlePrice(),
                                ProductStatus.UNPUBLISHED.name(),
                                null,
                                null));
        bundleItems.saveAll(
                request.items().stream()
                        .map(
                                item ->
                                        item.modelId() == null
                                                ? BundleItemEntity.createCharacterItem(
                                                        bundle, item.versionId(), item.quantity())
                                                : BundleItemEntity.createLedItem(
                                                        bundle, item.modelId(), item.quantity()))
                        .toList());

        return new CreatedBundleResponse(
                bundle.getBundleId(),
                product.getProductId(),
                request.bundleName(),
                request.description(),
                request.thumbnailUrl(),
                List.copyOf(product.getGalleryUrls()),
                originalPrice,
                request.bundlePrice(),
                bundle.getStatus(),
                ids,
                request.items());
    }

    private BigDecimal price(Item item) {
        var type = item.modelId() == null ? ProductType.CHARACTER : ProductType.LED;
        var productId =
                type == ProductType.LED
                        ? models.findByModelId(item.modelId()).map(model -> model.modelId())
                        : versions.findById(item.versionId())
                                .map(version -> version.getProductId());
        return productId
                .flatMap(products::findById)
                .filter(
                        product ->
                                product.getProductType() == type
                                        && product.getStatus() != ProductStatus.DELETED)
                .map(ProductEntity::getBasePrice)
                .orElseThrow(ProductNotFoundException::new);
    }
}
