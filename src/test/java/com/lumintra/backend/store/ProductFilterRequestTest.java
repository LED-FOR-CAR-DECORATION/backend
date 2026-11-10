package com.lumintra.backend.store;

import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.domain.model.ProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductFilterRequestTest {

    @Test
    @DisplayName("UTCID01 [N]: Default filter generates stable cache key")
    void defaultFilter_generatesExpectedKey() {
        ProductFilterRequest request = new ProductFilterRequest();
        assertThat(request.toCacheKey()).isEqualTo("p=0:s=20:t=ALL:tag=ALL:min=0:max=INF");
    }

    @Test
    @DisplayName("UTCID02 [N]: Filter with parameters normalizes tag and formats prices")
    void filterWithParams_normalizesAndFormatsKey() {
        ProductFilterRequest request = new ProductFilterRequest(
                ProductType.LED,
                "  CyberPunk  ",
                new BigDecimal("100.50"),
                new BigDecimal("500.00"),
                1,
                10
        );

        assertThat(request.toCacheKey()).isEqualTo("p=1:s=10:t=LED:tag=cyberpunk:min=100.5:max=500");
    }

    @Test
    @DisplayName("UTCID03 [N]: Zero prices are formatted as 0")
    void zeroPrices_formattedAsZero() {
        ProductFilterRequest request = new ProductFilterRequest(
                null,
                null,
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                0,
                20
        );

        assertThat(request.toCacheKey()).isEqualTo("p=0:s=20:t=ALL:tag=ALL:min=0:max=0");
    }

    @Test
    @DisplayName("UTCID04 [N]: Identical filters produce exact same key")
    void identicalFilters_produceSameKey() {
        ProductFilterRequest req1 = new ProductFilterRequest(ProductType.CHARACTER, "Anime", new BigDecimal("50"), null, 0, 20);
        ProductFilterRequest req2 = new ProductFilterRequest(ProductType.CHARACTER, "anime", new BigDecimal("50.00"), null, 0, 20);

        assertThat(req1.toCacheKey()).isEqualTo(req2.toCacheKey());
    }
}
