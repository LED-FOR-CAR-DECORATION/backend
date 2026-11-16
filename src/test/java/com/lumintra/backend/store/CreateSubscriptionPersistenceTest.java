package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.shared.exception.store.PlanCodeAlreadyExistsException;
import com.lumintra.backend.store.api.request.CreateSubscriptionRequest;
import com.lumintra.backend.store.application.CreateSubscriptionUseCase;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
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

@DataJpaTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:str02_query_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = CreateSubscriptionPersistenceTest.Config.class)
class CreateSubscriptionPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, CharacterVersionEntity.class})
    @EnableJpaRepositories(
            basePackageClasses = {ProductRepository.class, CharacterVersionRepository.class})
    @Import(CreateSubscriptionUseCase.class)
    static class Config {}

    @Autowired private EntityManager em;

    @Autowired private ProductRepository productRepository;

    @Autowired private BundleItemRepository bundleItemRepository;

    @Autowired private SubscriptionDetailRepository subscriptionDetailRepository;

    @Autowired private CharacterVersionRepository characterVersionRepository;

    @Autowired CreateSubscriptionUseCase useCase;

    @Test
    void savesUnpublishedSubscriptionAndRejectsDuplicate() {
        var request =
                new CreateSubscriptionRequest(
                        "Monthly",
                        " VIP_MONTHLY ",
                        BigDecimal.TEN,
                        "Membership",
                        null,
                        false,
                        30,
                        true,
                        null);
        var result = useCase.execute(request);
        assertThat(result.planCode()).isEqualTo("VIP_MONTHLY");
        assertThat(result.durationDays()).isEqualTo(30);
        assertThat(productRepository.findById(result.productId()).orElseThrow().getProductType())
                .isEqualTo(com.lumintra.backend.store.domain.model.ProductType.SUBSCRIPTION);
        assertThat(result.status())
                .isEqualTo(com.lumintra.backend.store.domain.model.ProductStatus.UNPUBLISHED);
        assertThat(subscriptionDetailRepository.findByProductId(result.productId())).isPresent();

        long productCount = productRepository.count();
        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(PlanCodeAlreadyExistsException.class);
        assertThat(productRepository.count()).isEqualTo(productCount);
    }
}
