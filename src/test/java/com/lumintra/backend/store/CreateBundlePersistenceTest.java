package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.shared.exception.store.InvalidBundlePriceException;
import com.lumintra.backend.store.api.request.CreateBundleRequest;
import com.lumintra.backend.store.api.request.CreateBundleRequest.Item;
import com.lumintra.backend.store.application.CreateBundleUseCase;
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
@ContextConfiguration(classes = CreateBundlePersistenceTest.Config.class)
class CreateBundlePersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, CharacterVersionEntity.class})
    @EnableJpaRepositories(
            basePackageClasses = {ProductRepository.class, CharacterVersionRepository.class})
    @Import(CreateBundleUseCase.class)
    static class Config {}

    @Autowired private EntityManager em;

    @Autowired private BundleRepository bundles;

    @Autowired private BundleItemRepository bundleItemRepository;

    @Autowired private SubscriptionDetailRepository subscriptionDetailRepository;

    @Autowired private CharacterVersionRepository characterVersionRepository;

    @Autowired CreateBundleUseCase useCase;
    @Autowired ProductRepository products;
    @MockitoBean LedModelLookupService models;

    @Test
    void createsProductBundleAndItemsWithCalculatedPrice() {
        var led = insertLed();
        var result = useCase.execute(request(led.getProductId(), "19.99"));
        em.flush();
        em.clear();

        assertThat(result.originalPrice()).isEqualByComparingTo("20.00");
        assertThat(products.findById(result.productId()).orElseThrow().getProductType())
                .isEqualTo(ProductType.BUNDLE);
        assertThat(bundles.findById(result.bundleId()).orElseThrow().getStatus())
                .isEqualTo("UNPUBLISHED");
        assertThat(bundleItemRepository.findAllByBundle_BundleId(result.bundleId()))
                .extracting(BundleItemEntity::getQuantity)
                .containsExactly(2);
    }

    @Test
    void rejectsBundlePriceAboveOriginalPriceWithoutSaving() {
        var led = insertLed();
        long count = products.count();
        assertThatThrownBy(() -> useCase.execute(request(led.getProductId(), "20.01")))
                .isInstanceOf(InvalidBundlePriceException.class);
        assertThat(products.count()).isEqualTo(count);
        assertThat(bundles.count()).isZero();
    }

    private ProductEntity insertLed() {
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
        return led;
    }

    private CreateBundleRequest request(long modelId, String price) {
        return new CreateBundleRequest(
                "Combo",
                null,
                null,
                List.of(),
                new BigDecimal(price),
                List.of(),
                List.of(new Item(modelId, null, 2)));
    }
}
