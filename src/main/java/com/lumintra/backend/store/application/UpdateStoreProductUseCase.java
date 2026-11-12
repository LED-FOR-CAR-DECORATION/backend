package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.store.api.request.UpdateStoreProductRequest;
import com.lumintra.backend.store.api.response.UpdatedStoreProductResponse;
import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdateStoreProductUseCase {
    private final ProductRepository products;
    private final TagRepository tags;
    private final ApplicationEventPublisher events;

    @Transactional
    public UpdatedStoreProductResponse execute(long id, UpdateStoreProductRequest request) {
        var product =
                products.findForManagementUpdate(id)
                        .filter(candidate -> candidate.getStatus() != ProductStatus.DELETED)
                        .orElseThrow(ProductNotFoundException::new);

        if (request.tagIds() != null) {
            var ids = request.tagIds().stream().distinct().toList();
            var selectedTags = ids.isEmpty() ? List.<TagEntity>of() : tags.findAllById(ids);
            if (selectedTags.size() != ids.size()) {
                throw new StoreProductTagNotFoundException();
            }
            product.getTags().clear();
            product.getTags().addAll(selectedTags);
        }

        product.setProductName(request.productName());
        product.setBasePrice(request.basePrice());
        product.setDescription(request.description());
        product.setThumbnailUrl(request.thumbnailUrl());
        product.setGalleryUrls(
                request.galleryUrls() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(request.galleryUrls()));

        events.publishEvent(new StoreProductUpdatedEvent(id));
        return new UpdatedStoreProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductType(),
                product.getBasePrice(),
                product.getDescription(),
                product.getThumbnailUrl(),
                List.copyOf(product.getGalleryUrls()),
                product.getStatus(),
                product.getTags().stream().map(TagEntity::getTagId).sorted().toList());
    }
}
