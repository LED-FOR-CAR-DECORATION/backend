package com.lumintra.backend.store.api.response;

import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.domain.model.ProductType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Builder
public record ProductListResponse(
        List<ProductItem> items,
        PaginationMeta meta
) {
    @Builder
    public record ProductItem(
            Long productId,
            String productName,
            String description,
            ProductType productType,
            BigDecimal basePrice,
            String thumbnailUrl,
            List<String> galleryUrls,
            List<TagItem> tags,
            Instant createdAt
    ) {
        @Builder
        public record TagItem(
                Long tagId,
                String name,
                String description
        ) {}
    }
}
