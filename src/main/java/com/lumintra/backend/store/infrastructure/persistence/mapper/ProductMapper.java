package com.lumintra.backend.store.infrastructure.persistence.mapper;

import com.lumintra.backend.store.api.response.ProductSummaryResponse;
import com.lumintra.backend.store.api.response.TagResponse;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class ProductMapper {

    public ProductSummaryResponse toSummaryResponse(ProductEntity entity) {
        if (entity == null) {
            return null;
        }

        return ProductSummaryResponse.builder()
                .productId(entity.getProductId())
                .productName(entity.getProductName())
                .description(entity.getDescription())
                .productType(entity.getProductType())
                .basePrice(entity.getBasePrice())
                .thumbnailUrl(entity.getThumbnailUrl())
                .galleryUrls(entity.getGalleryUrls() != null ? entity.getGalleryUrls() : Collections.emptyList())
                .tags(toTagResponses(entity.getTags()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public List<ProductSummaryResponse> toSummaryResponseList(List<ProductEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    public TagResponse toTagResponse(TagEntity entity) {
        if (entity == null) {
            return null;
        }
        return TagResponse.builder()
                .tagId(entity.getTagId())
                .name(entity.getName())
                .description(entity.getDescription())
                .build();
    }

    public List<TagResponse> toTagResponses(Set<TagEntity> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags.stream()
                .map(this::toTagResponse)
                .toList();
    }
}
