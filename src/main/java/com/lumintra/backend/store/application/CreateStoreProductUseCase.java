package com.lumintra.backend.store.application;

import com.lumintra.backend.character.domain.service.CharacterProductLinkService;
import com.lumintra.backend.device.domain.service.LedModelCreationService;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException.Reason;
import com.lumintra.backend.store.api.request.CreateStoreProductRequest;
import com.lumintra.backend.store.api.response.CreatedStoreProductResponse;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreateStoreProductUseCase {
    private final ProductRepository products;
    private final TagRepository tags;
    private final CharacterProductLinkService characters;
    private final LedModelCreationService ledModels;

    @Transactional
    public CreatedStoreProductResponse execute(CreateStoreProductRequest request) {
        var ids =
                request.tagIds() == null
                        ? List.<Long>of()
                        : request.tagIds().stream().distinct().toList();
        var selectedTags = ids.isEmpty() ? List.<TagEntity>of() : tags.findAllById(ids);
        if (selectedTags.size() != ids.size()) {
            throw new StoreProductReferenceException(
                    Reason.TAG_NOT_FOUND, "One or more tags were not found.");
        }
        var product =
                products.save(
                        ProductEntity.create(
                                request.productName(),
                                request.productType(),
                                request.basePrice(),
                                request.description(),
                                request.thumbnailUrl(),
                                request.galleryUrls(),
                                ProductStatus.UNPUBLISHED,
                                new HashSet<>(selectedTags)));

        CreatedStoreProductResponse.LedDetails createdLed = null;
        if (product.getProductType() == ProductType.CHARACTER) {
            characters.linkVersion(request.characterVersionId(), product.getProductId());
        } else if (request.ledDetails() != null) {
            var details = request.ledDetails();
            var spec = details.spec() == null ? Map.<String, Object>of() : details.spec();
            ledModels.createForProduct(
                    product.getProductId(), details.modelName(), spec, details.stockQuantity());
            createdLed =
                    new CreatedStoreProductResponse.LedDetails(
                            product.getProductId(),
                            details.modelName(),
                            spec,
                            details.stockQuantity());
        }

        return new CreatedStoreProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductType(),
                product.getBasePrice(),
                product.getDescription(),
                product.getThumbnailUrl(),
                List.copyOf(product.getGalleryUrls()),
                product.getStatus(),
                ids,
                request.characterVersionId(),
                createdLed);
    }
}
