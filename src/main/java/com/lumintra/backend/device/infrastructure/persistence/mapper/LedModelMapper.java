package com.lumintra.backend.device.infrastructure.persistence.mapper;

import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps persisted {@link LedModelEntity} rows into API catalog response DTOs.
 */
@Component
public class LedModelMapper {

    private static final Set<String> RESERVED_SPEC_KEYS = Set.of("widthPx", "heightPx", "attributes");

    public LedModelListResponse.LedModelItem toItem(LedModelEntity entity) {
        long stockQuantity = entity.getStockQuantity() != null ? entity.getStockQuantity() : 0L;

        return LedModelListResponse.LedModelItem.builder()
                .modelId(entity.getModelId())
                .productId(entity.getProductId())
                .modelName(entity.getModelName())
                .stockQuantity(stockQuantity)
                .isLowStock(stockQuantity < LedModelEntity.LOW_STOCK_THRESHOLD)
                .status(resolveStatus(entity))
                .spec(toSpec(entity))
                .build();
    }

    public LedModelStatus resolveStatus(LedModelEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getStatus() != null) {
            return entity.getStatus();
        }
        long stockQuantity = entity.getStockQuantity() != null ? entity.getStockQuantity() : 0L;
        return stockQuantity > 0L ? LedModelStatus.ACTIVE : LedModelStatus.OUT_OF_STOCK;
    }

    public LedModelListResponse.LedSpec toSpec(LedModelEntity entity) {
        Map<String, Object> rawSpec = entity.getSpec() != null ? entity.getSpec() : Map.of();
        int[] extracted = LedModelEntity.extractDimensions(rawSpec);

        Integer widthPx = entity.getWidthPx() != null
                ? entity.getWidthPx()
                : (extracted[0] > 0 ? extracted[0] : null);
        Integer heightPx = entity.getHeightPx() != null
                ? entity.getHeightPx()
                : (extracted[1] > 0 ? extracted[1] : null);

        return LedModelListResponse.LedSpec.builder()
                .widthPx(widthPx)
                .heightPx(heightPx)
                .attributes(extractAttributes(rawSpec))
                .build();
    }

    private List<LedModelListResponse.SpecAttribute> extractAttributes(Map<String, Object> rawSpec) {
        if (rawSpec.isEmpty()) {
            return List.of();
        }
        Object attributesObj = rawSpec.get("attributes");
        if (attributesObj instanceof List<?> rawList) {
            List<LedModelListResponse.SpecAttribute> ordered = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> entryMap) {
                    Object labelObj = entryMap.get("label");
                    if (labelObj != null) {
                        String label = String.valueOf(labelObj).strip();
                        if (!label.isEmpty()) {
                            Object valueObj = entryMap.get("value");
                            String value = valueObj == null ? "" : String.valueOf(valueObj);
                            ordered.add(new LedModelListResponse.SpecAttribute(label, value));
                        }
                    }
                }
            }
            return List.copyOf(ordered);
        }

        // Legacy flat spec fallback (maps top-level technical attributes in entry order)
        List<LedModelListResponse.SpecAttribute> fallback = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawSpec.entrySet()) {
            if (entry.getKey() == null || RESERVED_SPEC_KEYS.contains(entry.getKey())) {
                continue;
            }
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            fallback.add(new LedModelListResponse.SpecAttribute(entry.getKey(), value));
        }
        return List.copyOf(fallback);
    }
}
