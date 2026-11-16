package com.lumintra.backend.store.application;

import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.shared.exception.store.BundleNotFoundException;
import com.lumintra.backend.shared.exception.store.InvalidBundlePriceException;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.store.api.request.UpdateBundleRequest;
import com.lumintra.backend.store.api.request.UpdateBundleRequest.Item;
import com.lumintra.backend.store.api.response.UpdatedBundleResponse;
import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdateBundleUseCase {
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private final ProductRepository products;
    private final BundleRepository bundles;
    private final BundleItemRepository bundleItems;
    private final LedModelLookupService models;
    private final CharacterVersionRepository versions;
    private final ApplicationEventPublisher events;

    @Transactional
    public UpdatedBundleResponse execute(long id, UpdateBundleRequest request) {
        var bundle = bundles.findByBundleId(id).orElseThrow(BundleNotFoundException::new);
        var product =
                products.findForManagementUpdate(bundle.getProductId())
                        .filter(candidate -> candidate.getProductType() == ProductType.BUNDLE)
                        .orElseThrow(BundleNotFoundException::new);
        var existing = bundleItems.findAllByBundle_BundleIdOrderByBundleItemIdAsc(id);
        var items =
                request.items() == null
                        ? existing.stream()
                                .map(
                                        item ->
                                                new Item(
                                                        item.getModelId(),
                                                        item.getVersionId(),
                                                        item.getQuantity()))
                                .toList()
                        : request.items();
        var originalPrice =
                request.items() == null && bundle.getOriginalPrice() != null
                        ? bundle.getOriginalPrice()
                        : items.stream()
                                .map(
                                        item ->
                                                price(item)
                                                        .multiply(
                                                                BigDecimal.valueOf(
                                                                        item.quantity())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.bundlePrice().compareTo(originalPrice) > 0
                || originalPrice.compareTo(MAX_PRICE) > 0) {
            throw new InvalidBundlePriceException(
                    "Bundle price must not exceed original price, and original price must fit the"
                            + " supported range.");
        }
        if (request.items() != null) {
            bundleItems.deleteAll(existing);
            bundleItems.flush();
            bundleItems.saveAll(
                    items.stream()
                            .map(
                                    item ->
                                            item.modelId() == null
                                                    ? BundleItemEntity.createCharacterItem(
                                                            bundle,
                                                            item.versionId(),
                                                            item.quantity())
                                                    : BundleItemEntity.createLedItem(
                                                            bundle,
                                                            item.modelId(),
                                                            item.quantity()))
                            .toList());
        }

        bundle.setBundleName(request.bundleName());
        bundle.setDescription(request.description());
        bundle.setBundlePrice(request.bundlePrice());
        bundle.setOriginalPrice(originalPrice);
        product.setProductName(request.bundleName());
        product.setDescription(request.description());
        product.setThumbnailUrl(request.thumbnailUrl());
        product.setBasePrice(request.bundlePrice());
        events.publishEvent(new StoreProductUpdatedEvent(product.getProductId()));
        return new UpdatedBundleResponse(
                id,
                product.getProductId(),
                bundle.getBundleName(),
                bundle.getDescription(),
                product.getThumbnailUrl(),
                product.getGalleryUrls() == null
                        ? List.of()
                        : List.copyOf(product.getGalleryUrls()),
                originalPrice,
                bundle.getBundlePrice(),
                bundle.getStatus(),
                product.getTags().stream().map(TagEntity::getTagId).sorted().toList(),
                items);
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
