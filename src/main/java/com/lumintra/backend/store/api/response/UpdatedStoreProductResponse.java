package com.lumintra.backend.store.api.response;

import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;

import java.math.BigDecimal;
import java.util.List;

public record UpdatedStoreProductResponse(
        Long productId,
        String productName,
        ProductType productType,
        BigDecimal basePrice,
        String description,
        String thumbnailUrl,
        List<String> galleryUrls,
        ProductStatus status,
        List<Long> tagIds) {}
