package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.store.api.request.UpdateBundleRequest;
import com.lumintra.backend.store.api.request.UpdateBundleRequest.Item;
import com.lumintra.backend.store.application.UpdateBundleUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@DataJpaTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:str02_query_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = UpdateBundlePersistenceTest.Config.class)
class UpdateBundlePersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, CharacterVersionEntity.class})
    @EnableJpaRepositories(
            basePackageClasses = {ProductRepository.class, CharacterVersionRepository.class})
    @Import(UpdateBundleUseCase.class)
    static class Config {}

    @Autowired private EntityManager em;

    @Autowired private BundleRepository bundles;

    @Autowired private BundleItemRepository bundleItemRepository;

    @Autowired private SubscriptionDetailRepository subscriptionDetailRepository;

    @Autowired private CharacterVersionRepository characterVersionRepository;

    @Autowired UpdateBundleUseCase useCase;
    @Autowired ProductRepository products;
    @MockitoBean LedModelLookupService models;

    @Test
    void updatesBothPricesAndReplacesExistingComponents() {
        var led =
                products.save(
                        ProductEntity.create(
                                "LED",
                                ProductType.LED,
                                BigDecimal.TEN,
                                null,
                                null,
                                List.of(),
                                ProductStatus.PUBLISHED,
                                null));
        when(models.findByModelId(led.getProductId()))
                .thenReturn(
                        Optional.of(new LedModelSnapshot(led.getProductId(), "LED", Map.of(), 1L)));
        var product =
                products.save(
                        ProductEntity.create(
                                "Old bundle",
                                ProductType.BUNDLE,
                                BigDecimal.ONE,
                                null,
                                null,
                                List.of(),
                                ProductStatus.UNPUBLISHED,
                                null));
        var bundle =
                bundles.save(
                        com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity
                                .create(
                                        product.getProductId(),
                                        "Old bundle",
                                        null,
                                        BigDecimal.TEN,
                                        BigDecimal.ONE,
                                        "UNPUBLISHED",
                                        null,
                                        null));
        bundleItemRepository.save(BundleItemEntity.createLedItem(bundle, led.getProductId(), 1));
        var request =
                new UpdateBundleRequest(
                        "New bundle",
                        "Description",
                        null,
                        new BigDecimal("18.00"),
                        List.of(new Item(led.getProductId(), null, 2)));

        var response = useCase.execute(bundle.getBundleId(), request);
        em.flush();
        em.clear();

        assertThat(response.originalPrice()).isEqualByComparingTo("20.00");
        assertThat(products.findById(product.getProductId()).orElseThrow().getBasePrice())
                .isEqualByComparingTo("18.00");
        assertThat(bundles.findById(bundle.getBundleId()).orElseThrow().getBundlePrice())
                .isEqualByComparingTo("18.00");
        assertThat(bundleItemRepository.findAllByBundle_BundleId(bundle.getBundleId()))
                .extracting(BundleItemEntity::getQuantity)
                .containsExactly(2);
    }

    @Test
    void returnsMissingBundleError() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        Long.MAX_VALUE,
                                        new UpdateBundleRequest(
                                                "Name", null, null, BigDecimal.ZERO, null)))
                .isInstanceOf(
                        com.lumintra.backend.shared.exception.store.BundleNotFoundException.class);
    }
}
