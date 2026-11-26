package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.store.api.request.UpdateStoreProductStatusRequest;
import com.lumintra.backend.store.api.request.UpdateStoreProductStatusRequest.Status;
import com.lumintra.backend.store.application.UpdateStoreProductStatusUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.caching.StoreProductUpdatedCacheListener;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductSpecifications;

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
import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@DataJpaTest(
        properties = {
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = UpdateStoreProductStatusPersistenceTest.Config.class)
class UpdateStoreProductStatusPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ProductEntity.class)
    @EnableJpaRepositories(basePackageClasses = ProductRepository.class)
    @Import({UpdateStoreProductStatusUseCase.class, StoreProductUpdatedCacheListener.class})
    static class Config {}

    @Autowired EntityManager em;

    @Autowired UpdateStoreProductStatusUseCase useCase;
    @MockitoBean StoreCacheEvictor cacheEvictor;

    @Autowired ProductRepository productRepository;

    @Test
    void archiveKeepsPublicDetailButRemovesProductFromCatalogAndEvictsAfterCommit() {
        long id = insertProduct(ProductStatus.PUBLISHED);
        assertThat(productRepository.findAll(ProductSpecifications.withFilter(null)))
                .extracting(ProductEntity::getProductId)
                .contains(id);
        useCase.execute(id, request(Status.ARCHIVED));
        em.flush();
        em.clear();
        assertThat(productRepository.findAll(ProductSpecifications.withFilter(null)))
                .extracting(ProductEntity::getProductId)
                .doesNotContain(id);
        assertThat(
                        productRepository.findWithTagsByProductIdAndStatusIn(
                                id, Set.of(ProductStatus.PUBLISHED, ProductStatus.ARCHIVED)))
                .isPresent();
        verifyNoInteractions(cacheEvictor);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(cacheEvictor).evictProductAndAllProducts(id);
    }

    @Test
    void canPublishAndUnpublishAnArchivedProduct() {
        long id = insertProduct(ProductStatus.ARCHIVED);
        assertThat(useCase.execute(id, request(Status.PUBLISHED)).status())
                .isEqualTo(ProductStatus.PUBLISHED);
        assertThat(useCase.execute(id, request(Status.UNPUBLISHED)).status())
                .isEqualTo(ProductStatus.UNPUBLISHED);
        assertThat(
                        productRepository.findWithTagsByProductIdAndStatusIn(
                                id, Set.of(ProductStatus.PUBLISHED, ProductStatus.ARCHIVED)))
                .isEmpty();
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verifyNoInteractions(cacheEvictor);
    }

    @Test
    void repeatedStatusDoesNotEvict() {
        long id = insertProduct(ProductStatus.PUBLISHED);
        assertThat(useCase.execute(id, request(Status.PUBLISHED)).status())
                .isEqualTo(ProductStatus.PUBLISHED);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verifyNoInteractions(cacheEvictor);
    }

    @Test
    void rejectsMissingAndDeletedProducts() {
        long deletedId = insertProduct(ProductStatus.DELETED);
        assertThatThrownBy(() -> useCase.execute(Long.MAX_VALUE, request(Status.PUBLISHED)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute(deletedId, request(Status.PUBLISHED)))
                .isInstanceOf(ProductNotFoundException.class);
        verifyNoInteractions(cacheEvictor);
    }

    private long insertProduct(ProductStatus status) {
        var product =
                ProductEntity.create(
                        "LED",
                        ProductType.LED,
                        BigDecimal.TEN,
                        null,
                        null,
                        List.of(),
                        status,
                        Set.of());
        em.persist(product);
        return product.getProductId();
    }

    private UpdateStoreProductStatusRequest request(Status status) {
        return new UpdateStoreProductStatusRequest(status);
    }
}
