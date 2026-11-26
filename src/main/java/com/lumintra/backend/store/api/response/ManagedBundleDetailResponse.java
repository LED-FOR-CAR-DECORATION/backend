package com.lumintra.backend.store.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Managed store bundle detail payload including resolved component items")
@Builder
public record ManagedBundleDetailResponse(
        Long bundleId,
        Long productId,
        String bundleName,
        String description,
        String thumbnailUrl,
        List<String> galleryUrls,
        BigDecimal originalPrice,
        BigDecimal bundlePrice,
        String status,
        List<Long> tagIds,
        List<ProductDetailResponse.BundleItemDetail> items
) {}
