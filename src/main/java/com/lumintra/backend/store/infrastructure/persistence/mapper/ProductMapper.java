package com.lumintra.backend.store.infrastructure.persistence.mapper;

import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.api.response.ProductListResponse;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
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
}
