package com.lumintra.backend.device.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record LedModelSnapshot(
        Long modelId,
        String modelName,
        Map<String, Object> spec,
        Long stockQuantity
) {
    public LedModelSnapshot {
        spec = spec == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(spec));
    }
}
