package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.character.infrastructure.persistence.CharacterProductLinkServiceImpl;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.LedModelCreationServiceImpl;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException.Reason;
import com.lumintra.backend.store.api.request.CreateStoreProductRequest;
import com.lumintra.backend.store.application.CreateStoreProductUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@DataJpaTest(
        properties = {
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = CreateStoreProductIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CreateStoreProductIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(
            basePackageClasses = {
                ProductEntity.class,
                CharacterVersionEntity.class,
                LedModelEntity.class
            })
    @EnableJpaRepositories(
            basePackageClasses = {
                ProductRepository.class,
                CharacterVersionRepository.class,
                LedModelRepository.class
            })
    @Import({
        CreateStoreProductUseCase.class,
        CharacterProductLinkServiceImpl.class,
        LedModelCreationServiceImpl.class
    })
    static class Config {}

    @Autowired CreateStoreProductUseCase useCase;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired ProductRepository products;
    @Autowired CharacterVersionRepository versions;

    @Test
    void createsUnpublishedLedWithExistingTagsAndSharedModelId() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        tx -> {
                            var tag = TagEntity.create("Hardware", null);
                            em.persist(tag);
                            var details =
                                    new CreateStoreProductRequest.LedDetails(
                                            "LED board", Map.of("width", 32), 10L);
                            var result =
                                    useCase.execute(
                                            request(
                                                    ProductType.LED,
                                                    List.of(tag.getTagId(), tag.getTagId()),
                                                    null,
                                                    details));

                            em.flush();
                            em.clear();
                            var stored = em.find(ProductEntity.class, result.productId());
                            var model = em.find(LedModelEntity.class, result.productId());
                            assertThat(stored.getStatus()).isEqualTo(ProductStatus.UNPUBLISHED);
                            assertThat(stored.getTags())
                                    .extracting(TagEntity::getTagId)
                                    .containsExactly(tag.getTagId());
                            assertThat(model.getModelName()).isEqualTo("LED board");
                            assertThat(model.getStockQuantity()).isEqualTo(10);
                            assertThat(result.ledDetails().modelId()).isEqualTo(result.productId());
                            tx.setRollbackOnly();
                        });
    }

    @Test
    void supportsLedWithoutModelAndLinksCharacterVersion() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        tx -> {
                            var led = useCase.execute(request(ProductType.LED, null, null, null));
                            assertThat(em.find(LedModelEntity.class, led.productId())).isNull();

                            var character = CharacterEntity.create(null, "Demo art", null);
                            em.persist(character);
                            var version =
                                    CharacterVersionEntity.create(
                                            character,
                                            null,
                                            1,
                                            "https://example.test/preview.gif",
                                            null,
                                            true);
                            em.persist(version);
                            var result =
                                    useCase.execute(
                                            request(
                                                    ProductType.CHARACTER,
                                                    null,
                                                    version.getVersionId(),
                                                    null));
                            em.flush();
                            em.clear();
                            assertThat(
                                            versions.findById(version.getVersionId())
                                                    .orElseThrow()
                                                    .getProductId())
                                    .isEqualTo(result.productId());
                            assertThat(result.status()).isEqualTo(ProductStatus.UNPUBLISHED);
                            assertThatThrownBy(
                                            () ->
                                                    useCase.execute(
                                                            request(
                                                                    ProductType.CHARACTER,
                                                                    null,
                                                                    version.getVersionId(),
                                                                    null)))
                                    .isInstanceOfSatisfying(
                                            StoreProductReferenceException.class,
                                            ex ->
                                                    assertThat(ex.getReason())
                                                            .isEqualTo(
                                                                    Reason
                                                                            .CHARACTER_VERSION_ALREADY_LINKED));
                            tx.setRollbackOnly();
                        });
    }

    @Test
    void rollsBackProductWhenCharacterVersionIsMissing() {
        long before = products.count();
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        request(ProductType.CHARACTER, null, Long.MAX_VALUE, null)))
                .isInstanceOfSatisfying(
                        StoreProductReferenceException.class,
                        ex ->
                                assertThat(ex.getReason())
                                        .isEqualTo(Reason.CHARACTER_VERSION_NOT_FOUND));
        assertThat(products.count()).isEqualTo(before);
    }

    @Test
    void rejectsUnknownTagsBeforeCreation() {
        long before = products.count();
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        request(
                                                ProductType.LED,
                                                List.of(Long.MAX_VALUE),
                                                null,
                                                null)))
                .isInstanceOfSatisfying(
                        StoreProductReferenceException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Reason.TAG_NOT_FOUND));
        assertThat(products.count()).isEqualTo(before);
    }

    private CreateStoreProductRequest request(
            ProductType type,
            List<Long> tags,
            Long version,
            CreateStoreProductRequest.LedDetails details) {
        return new CreateStoreProductRequest(
                "Product", type, BigDecimal.ZERO, null, null, null, tags, version, details);
    }
}
