package com.lumintra.backend.store.api.response;

import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder
public record ManagedStoreProductResponse(
        List<Item> items,
        PaginationMeta meta
) {
    @Builder
    public record Item(
            Long productId,
            String productName,
            String description,
            ProductType productType,
            ProductStatus status,
            BigDecimal basePrice,
            String thumbnailUrl,
            List<String> galleryUrls,
            List<ProductListResponse.ProductItem.TagItem> tags,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
