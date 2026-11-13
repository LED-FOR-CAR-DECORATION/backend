package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.LedModelFilterRequest;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:led_model_spec_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@ContextConfiguration(classes = LedModelSpecificationsPersistenceTest.Config.class)
class LedModelSpecificationsPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LedModelEntity.class)
    @EnableJpaRepositories(basePackageClasses = LedModelRepository.class)
    static class Config {}

    @Autowired
    private LedModelRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();

        LedModelEntity proLinked = LedModelEntity.createForProduct(
                101L,
                "Lumintra Matrix 64x64 Pro",
                Map.of("widthPx", 64, "heightPx", 64),
                50L
        );

        LedModelEntity compactUnlinkedLowStock = LedModelEntity.createCatalogModel(
                201L,
                null,
                "Lumintra Compact 32x64",
                32,
                64,
                Map.of("widthPx", 32, "heightPx", 64),
                4L,
                LedModelStatus.ACTIVE
        );

        LedModelEntity exactBoundaryStockFive = LedModelEntity.createCatalogModel(
                202L,
                null,
                "Lumintra Panel 64x64 Stock5",
                64,
                64,
                Map.of("widthPx", 64, "heightPx", 64),
                5L,
                LedModelStatus.ACTIVE
        );

        LedModelEntity outOfStockLinked = LedModelEntity.createCatalogModel(
                102L,
                102L,
                "Lumintra Legacy 32x32",
                32,
                32,
                Map.of("resolution", "32x32"),
                0L,
                LedModelStatus.OUT_OF_STOCK
        );

        LedModelEntity deprecatedWithWildcard = LedModelEntity.createCatalogModel(
                203L,
                null,
                "Lumintra 64%Pro_Beta",
                64,
                64,
                Map.of("widthPx", 64, "heightPx", 64),
                2L,
                LedModelStatus.DEPRECATED
        );

        repository.saveAllAndFlush(List.of(
                proLinked,
                compactUnlinkedLowStock,
                exactBoundaryStockFive,
                outOfStockLinked,
                deprecatedWithWildcard
        ));
    }

    @Test
    @DisplayName("DEV-01 UTCID13 [N]: Supports multiple unlinked models (productId NULL) and deterministic DESC modelId sort")
    void findAll_unfiltered_returnsAllModelsInDescendingOrderIncludingMultipleNullProductIds() {
        Page<LedModelEntity> page = repository.findAll(
                LedModelSpecifications.withFilter(new LedModelFilterRequest(0, 20, null, null, null, null, null, null)),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modelId"))
        );

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent())
                .extracting(LedModelEntity::getModelId)
                .containsExactly(203L, 202L, 201L, 102L, 101L);
    }

    @Test
    @DisplayName("DEV-01 UTCID14 [N]: Filters by status, widthPx, and heightPx individually and together")
    void findAll_statusAndDimensions_filtersCorrectly() {
        List<LedModelEntity> active64x64 = repository.findAll(
                LedModelSpecifications.withFilter(
                        new LedModelFilterRequest(0, 20, LedModelStatus.ACTIVE, 64, 64, null, null, null)),
                Sort.by(Sort.Direction.DESC, "modelId")
        );
        assertThat(active64x64)
                .extracting(LedModelEntity::getModelId)
                .containsExactly(202L, 101L);

        List<LedModelEntity> width32 = repository.findAll(
                LedModelSpecifications.withFilter(
                        new LedModelFilterRequest(0, 20, null, 32, null, null, null, null)),
                Sort.by(Sort.Direction.DESC, "modelId")
        );
        assertThat(width32)
                .extracting(LedModelEntity::getModelId)
                .containsExactly(201L, 102L);

        List<LedModelEntity> outOfStock = repository.findAll(
                LedModelSpecifications.withFilter(
                        new LedModelFilterRequest(0, 20, LedModelStatus.OUT_OF_STOCK, null, null, null, null, null))
        );
        assertThat(outOfStock)
                .extracting(LedModelEntity::getModelId)
                .containsExactly(102L);
    }

    @Test
    @DisplayName("DEV-01 UTCID15 [N/B]: unlinkedToProductOnly selects NULL productId and lowStockOnly strictly matches stockQuantity < 5")
    void findAll_unlinkedAndLowStockFlags_applyCorrectBoundaries() {
        List<LedModelEntity> unlinked = repository.findAll(
                LedModelSpecifications.withFilter(
                        new LedModelFilterRequest(0, 20, null, null, null, true, null, null)),
                Sort.by(Sort.Direction.DESC, "modelId")
        );
        assertThat(unlinked)
                .extracting(LedModelEntity::getModelId)
                .containsExactly(203L, 202L, 201L);

        List<LedModelEntity> lowStock = repository.findAll(
                LedModelSpecifications.withFilter(
                        new LedModelFilterRequest(0, 20, null, null, null, null, true, null)),
                Sort.by(Sort.Direction.DESC, "modelId")
        );
        // stock 5 (modelId 202) MUST be excluded; stock 2, 4, 0 (203, 201, 102) MUST be included
        assertThat(lowStock)
                .extracting(LedModelEntity::getModelId)
                .containsExactly(203L, 201L, 102L);
    }

    @Test
    @DisplayName("DEV-01 UTCID16 [N/B]: keyword performs case-insensitive literal substring match and escapes SQL LIKE wildcards")
    void findAll_keyword_matchesCaseInsensitiveAndEscapesWildcards() {
        List<LedModelEntity> matrixUpper = repository.findAll(
                LedModelSpecifications.withFilter(
                        new LedModelFilterRequest(0, 20, null, null, null, null, null, "MATRIX 64X64")),
                Sort.by(Sort.Direction.DESC, "modelId")
        );
        assertThat(matrixUpper)
                .extracting(LedModelEntity::getModelId)
                .containsExactly(101L);

        List<LedModelEntity> literalWildcard = repository.findAll(
                LedModelSpecifications.withFilter(
                        new LedModelFilterRequest(0, 20, null, null, null, null, null, "64%pro_")),
                Sort.by(Sort.Direction.DESC, "modelId")
        );
        assertThat(literalWildcard)
                .extracting(LedModelEntity::getModelId)
                .containsExactly(203L);
    }
}
