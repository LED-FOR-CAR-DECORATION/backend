package com.lumintra.backend.store.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BundleSnapshot(
        Long bundleId,
        Long productId,
        String bundleName,
        BigDecimal bundlePrice,
        String status,
        Instant startAt,
        Instant endAt,
        List<BundleItemSnapshot> items
) {
    public record BundleItemSnapshot(
            Long bundleItemId,
            BundleItemType itemType,
            Long modelId,
            Long versionId,
            int quantity
    ) {}
}
