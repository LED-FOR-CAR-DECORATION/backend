package com.lumintra.backend.store.api.response;

import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "Newly created store product and its requested links")
public record CreatedStoreProductResponse(
        Long productId,
        String productName,
        ProductType productType,
        BigDecimal basePrice,
        String description,
        String thumbnailUrl,
        List<String> galleryUrls,
        ProductStatus status,
        List<Long> tagIds,
        Long characterVersionId,
        LedDetails ledDetails) {

    public record LedDetails(
            Long modelId, String modelName, Map<String, Object> spec, Long stockQuantity) {}
}
