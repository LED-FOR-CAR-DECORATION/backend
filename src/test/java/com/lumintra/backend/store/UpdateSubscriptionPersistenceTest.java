package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.shared.exception.store.InvalidSubscriptionDurationException;
import com.lumintra.backend.shared.exception.store.SubscriptionNotFoundException;
import com.lumintra.backend.store.api.request.UpdateSubscriptionRequest;
import com.lumintra.backend.store.application.UpdateSubscriptionUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@DataJpaTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:str02_query_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = UpdateSubscriptionPersistenceTest.Config.class)
class UpdateSubscriptionPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, CharacterVersionEntity.class})
    @EnableJpaRepositories(
            basePackageClasses = {ProductRepository.class, CharacterVersionRepository.class})
    @Import(UpdateSubscriptionUseCase.class)
    static class Config {}

    @Autowired private EntityManager em;

    @Autowired private ProductRepository productRepository;

    @Autowired private BundleItemRepository bundleItemRepository;

    @Autowired private SubscriptionDetailRepository subscriptionDetailRepository;

    @Autowired private CharacterVersionRepository characterVersionRepository;

    @Autowired UpdateSubscriptionUseCase useCase;

    @Test
    void updatesPlanAndProductWhilePreservingCodeAndStatus() {
        var product =
                productRepository.save(
                        ProductEntity.create(
                                "Old",
                                ProductType.SUBSCRIPTION,
                                BigDecimal.ONE,
                                null,
                                null,
                                List.of(),
                                ProductStatus.PUBLISHED,
                                Set.of()));
        var detail =
                subscriptionDetailRepository.save(
                        SubscriptionDetailEntity.create(
                                product.getProductId(),
                                "FIXED",
                                "Old",
                                false,
                                30,
                                true,
                                0,
                                3,
                                Map.of(),
                                null));

        var response =
                useCase.execute(
                        detail.getSubscriptionDetailId(),
                        new UpdateSubscriptionRequest(
                                "New", BigDecimal.TEN, "New description", null, 60, false));
        em.flush();
        em.clear();

        var saved =
                subscriptionDetailRepository
                        .findById(detail.getSubscriptionDetailId())
                        .orElseThrow();
        assertThat(saved.getPlanCode()).isEqualTo("FIXED");
        assertThat(saved.getPlanName()).isEqualTo("New");
        assertThat(saved.getDurationDays()).isEqualTo(60);
        assertThat(saved.isAllAccessCharacters()).isFalse();
        assertThat(productRepository.findById(product.getProductId()).orElseThrow().getBasePrice())
                .isEqualByComparingTo("10");
        assertThat(response.status()).isEqualTo(ProductStatus.PUBLISHED);
    }

    @Test
    void rejectsMissingPlanAndDurationForLifetimePlan() {
        var request = new UpdateSubscriptionRequest("New", BigDecimal.TEN, null, null, 30, true);
        assertThatThrownBy(() -> useCase.execute(Long.MAX_VALUE, request))
                .isInstanceOf(SubscriptionNotFoundException.class);

        var product =
                productRepository.save(
                        ProductEntity.create(
                                "Lifetime",
                                ProductType.SUBSCRIPTION,
                                BigDecimal.ONE,
                                null,
                                null,
                                List.of(),
                                ProductStatus.PUBLISHED,
                                Set.of()));
        var detail =
                subscriptionDetailRepository.save(
                        SubscriptionDetailEntity.create(
                                product.getProductId(),
                                "LIFE",
                                "Lifetime",
                                true,
                                null,
                                true,
                                0,
                                3,
                                Map.of(),
                                null));
        assertThatThrownBy(() -> useCase.execute(detail.getSubscriptionDetailId(), request))
                .isInstanceOf(InvalidSubscriptionDurationException.class);
        assertThat(detail.getPlanName()).isEqualTo("Lifetime");
    }
}
