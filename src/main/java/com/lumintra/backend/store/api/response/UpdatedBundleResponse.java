package com.lumintra.backend.store.api.response;

import com.lumintra.backend.store.api.request.UpdateBundleRequest.Item;

import java.math.BigDecimal;
import java.util.List;

public record UpdatedBundleResponse(
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
        List<Item> items) {}
