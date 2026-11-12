package com.lumintra.backend.device.domain.service;

import java.util.Map;

public interface LedModelCreationService {
    void createForProduct(
            long productId, String modelName, Map<String, Object> spec, long stockQuantity);
}
