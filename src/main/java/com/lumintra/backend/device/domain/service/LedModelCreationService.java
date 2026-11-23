package com.lumintra.backend.device.domain.service;

import com.lumintra.backend.device.domain.model.LedModelSnapshot;

import java.util.Map;

public interface LedModelCreationService {
    long createForProduct(
            long productId, String modelName, Map<String, Object> spec, long stockQuantity);

    LedModelSnapshot linkExistingToProduct(long modelId, long productId);
}
