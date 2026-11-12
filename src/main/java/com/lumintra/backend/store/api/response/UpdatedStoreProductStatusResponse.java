package com.lumintra.backend.store.api.response;

import com.lumintra.backend.store.domain.model.ProductStatus;

public record UpdatedStoreProductStatusResponse(Long productId, ProductStatus status) {}
