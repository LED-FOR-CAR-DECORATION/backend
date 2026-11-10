package com.lumintra.backend.store.api.request;

import com.lumintra.backend.store.api.request.validation.ValidPriceRange;
import com.lumintra.backend.store.domain.model.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

@ValidPriceRange
@Schema(description = "Filter parameters for browsing products in store catalog")
public record ProductFilterRequest(
        @Schema(description = "Filter by product type", example = "LED")
        ProductType productType,

        @Schema(description = "Filter by tag/category name", example = "Cyberpunk")
        String categoryTag,

        @Schema(description = "Minimum price", example = "100000")
        @Min(value = 0, message = "minPrice must be greater than or equal to 0")
        BigDecimal minPrice,

        @Schema(description = "Maximum price", example = "500000")
        @Min(value = 0, message = "maxPrice must be greater than or equal to 0")
        BigDecimal maxPrice,

        @Schema(description = "Page number (0-indexed)", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page must be greater than or equal to 0")
        Integer page,

        @Schema(description = "Page size (1-100)", example = "20", defaultValue = "20")
        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must not exceed 100")
        Integer size
) {
    public ProductFilterRequest {
        page = (page != null) ? page : 0;
        size = (size != null) ? size : 20;
    }

    public ProductFilterRequest() {
        this(null, null, null, null, 0, 20);
    }

    public ProductFilterRequest(ProductType productType, String categoryTag) {
        this(productType, categoryTag, null, null, 0, 20);
    }

    public ProductFilterRequest(BigDecimal minPrice, BigDecimal maxPrice) {
        this(null, null, minPrice, maxPrice, 0, 20);
    }

    public ProductFilterRequest(Integer page, Integer size) {
        this(null, null, null, null, page, size);
    }

    /**
     * Generates a deterministic, namespaced cache key segment for catalog browsing queries.
     * Example: "p=0:s=20:t=LED:tag=ambient:min=100000:max=500000"
     */
    public String toCacheKey() {
        String tag = (categoryTag != null && !categoryTag.isBlank()) ? categoryTag.trim().toLowerCase() : "ALL";
        String type = (productType != null) ? productType.name() : "ALL";
        String min = (minPrice != null) ? (minPrice.compareTo(BigDecimal.ZERO) == 0 ? "0" : minPrice.stripTrailingZeros().toPlainString()) : "0";
        String max = (maxPrice != null) ? (maxPrice.compareTo(BigDecimal.ZERO) == 0 ? "0" : maxPrice.stripTrailingZeros().toPlainString()) : "INF";
        return String.format("p=%d:s=%d:t=%s:tag=%s:min=%s:max=%s", page, size, type, tag, min, max);
    }
}
