package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.api.response.ProductSummaryResponse;

import java.util.List;

public record ProductPageResult(
        List<ProductSummaryResponse> items,
        PaginationMeta meta
) {}
