package com.lumintra.backend.device;

import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LedModelMapperTest {

    private final LedModelMapper mapper = new LedModelMapper();

    @Test
    @DisplayName("DEV-01 UTCID10 [N]: Maps canonical spec JSONB preserving exact attribute order")
    void toItem_canonicalSpec_preservesAttributeOrderAndFields() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("widthPx", 64);
        spec.put("heightPx", 64);
        spec.put("attributes", List.of(
                Map.of("label", "Driver", "value", "HUB75E"),
                Map.of("label", "Pixel Pitch", "value", "P3"),
                Map.of("label", "Brightness", "value", "1200 nits")
        ));

        LedModelEntity entity = LedModelEntity.createCatalogModel(
                201L, 101L, "Lumintra Matrix 64x64 Pro", 64, 64, spec, 50L, LedModelStatus.ACTIVE);

        LedModelListResponse.LedModelItem item = mapper.toItem(entity);

        assertThat(item.modelId()).isEqualTo(201L);
        assertThat(item.productId()).isEqualTo(101L);
        assertThat(item.modelName()).isEqualTo("Lumintra Matrix 64x64 Pro");
        assertThat(item.stockQuantity()).isEqualTo(50L);
        assertThat(item.isLowStock()).isFalse();
        assertThat(item.status()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(item.spec().widthPx()).isEqualTo(64);
        assertThat(item.spec().heightPx()).isEqualTo(64);
        assertThat(item.spec().attributes()).containsExactly(
                new LedModelListResponse.SpecAttribute("Driver", "HUB75E"),
                new LedModelListResponse.SpecAttribute("Pixel Pitch", "P3"),
                new LedModelListResponse.SpecAttribute("Brightness", "1200 nits")
        );
    }

    @Test
    @DisplayName("DEV-01 UTCID11 [N]: Maps legacy flat spec by extracting dimensions and converting non-reserved keys to attributes")
    void toItem_legacyFlatSpec_extractsDimensionsAndFallbackAttributes() {
        Map<String, Object> legacySpec = new LinkedHashMap<>();
        legacySpec.put("resolution", "32x64");
        legacySpec.put("dimensionsMm", "96x192x18mm");
        legacySpec.put("brightnessNits", 1000);

        LedModelEntity entity = LedModelEntity.create(202L, "Lumintra Matrix 32x64", legacySpec, 10L);

        LedModelListResponse.LedModelItem item = mapper.toItem(entity);

        assertThat(item.productId()).isNull();
        assertThat(item.spec().widthPx()).isEqualTo(32);
        assertThat(item.spec().heightPx()).isEqualTo(64);
        assertThat(item.spec().attributes()).containsExactly(
                new LedModelListResponse.SpecAttribute("resolution", "32x64"),
                new LedModelListResponse.SpecAttribute("dimensionsMm", "96x192x18mm"),
                new LedModelListResponse.SpecAttribute("brightnessNits", "1000")
        );
    }

    @Test
    @DisplayName("DEV-01 UTCID12 [B]: Computes isLowStock at boundary 4 vs 5 and ignores malformed/null attributes safely")
    void toItem_lowStockBoundaryAndMalformedSpec_handlesSafely() {
        List<Object> dirtyAttributes = new ArrayList<>();
        dirtyAttributes.add(null);
        dirtyAttributes.add("not-a-map");
        dirtyAttributes.add(Map.of("value", "missing-label"));
        dirtyAttributes.add(Map.of("label", "  ", "value", "blank-label"));
        dirtyAttributes.add(Map.of("label", "Voltage", "value", 5));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("attributes", dirtyAttributes);

        LedModelEntity stockFour = LedModelEntity.createCatalogModel(
                1L, null, "Low Stock 4", null, null, spec, 4L, null);
        LedModelEntity stockFive = LedModelEntity.createCatalogModel(
                2L, null, "Healthy Stock 5", null, null, null, 5L, null);
        LedModelEntity stockZero = LedModelEntity.createCatalogModel(
                3L, null, "Zero Stock", null, null, null, 0L, null);

        LedModelListResponse.LedModelItem mappedFour = mapper.toItem(stockFour);
        LedModelListResponse.LedModelItem mappedFive = mapper.toItem(stockFive);
        LedModelListResponse.LedModelItem mappedZero = mapper.toItem(stockZero);

        assertThat(mappedFour.isLowStock()).isTrue();
        assertThat(mappedFour.status()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(mappedFour.spec().widthPx()).isNull();
        assertThat(mappedFour.spec().heightPx()).isNull();
        assertThat(mappedFour.spec().attributes())
                .containsExactly(new LedModelListResponse.SpecAttribute("Voltage", "5"));

        assertThat(mappedFive.isLowStock()).isFalse();
        assertThat(mappedFive.status()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(mappedFive.spec().attributes()).isEmpty();

        assertThat(mappedZero.isLowStock()).isTrue();
        assertThat(mappedZero.status()).isEqualTo(LedModelStatus.OUT_OF_STOCK);
    }
}
