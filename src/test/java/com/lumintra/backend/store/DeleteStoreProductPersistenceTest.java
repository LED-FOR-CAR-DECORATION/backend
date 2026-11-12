package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.store.application.DeleteStoreProductUseCase;
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
import java.time.Instant;
import java.util.List;
import java.util.Set;

@DataJpaTest(
        properties = {
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = DeleteStoreProductPersistenceTest.Config.class)
class DeleteStoreProductPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, OrderEntity.class})
    @EnableJpaRepositories(basePackageClasses = ProductRepository.class)
    @Import({DeleteStoreProductUseCase.class, StoreProductUpdatedCacheListener.class})
    static class Config {}

    @Autowired EntityManager em;
    @Autowired DeleteStoreProductUseCase useCase;
    @MockitoBean StoreCacheEvictor cacheEvictor;

    @Autowired ProductRepository products;

    @Test
    void keepsProductAndOrderItemButHidesProductFromCatalogAndEvictsAfterCommit() {
        long id = insertProduct();
        var price = new BigDecimal("20.00");
        var order =
                OrderEntity.createPendingPayment(
                        1L,
                        null,
                        price,
                        BigDecimal.ZERO,
                        price,
                        OrderType.DIGITAL,
                        Instant.now().plusSeconds(600));
        em.persist(order);
        var item = OrderItemEntity.create(order, id, 1, price);
        em.persist(item);
        em.flush();
        em.clear();

        useCase.execute(id);
        em.flush();
        em.clear();
        assertThat(em.find(ProductEntity.class, id).getStatus()).isEqualTo(ProductStatus.DELETED);
        assertThat(em.find(OrderItemEntity.class, item.getOrderItemId()).getProductId())
                .isEqualTo(id);
        assertThat(products.findAll(ProductSpecifications.withFilter(null)))
                .extracting(ProductEntity::getProductId)
                .doesNotContain(id);
        assertThat(
                        products.findWithTagsByProductIdAndStatusIn(
                                id, Set.of(ProductStatus.PUBLISHED, ProductStatus.ARCHIVED)))
                .isEmpty();

        verifyNoInteractions(cacheEvictor);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(cacheEvictor).evictProductAndAllProducts(id);
    }

    @Test
    void rejectsMissingAndAlreadyDeletedProductsAndSkipsEvictionOnRollback() {
        long id = insertProduct();
        useCase.execute(id);
        assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute(Long.MAX_VALUE))
                .isInstanceOf(ProductNotFoundException.class);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verifyNoInteractions(cacheEvictor);
    }

    private long insertProduct() {
        var product =
                ProductEntity.create(
                        "Original",
                        ProductType.CHARACTER,
                        new BigDecimal("20.00"),
                        null,
                        null,
                        List.of(),
                        ProductStatus.PUBLISHED,
                        Set.of());
        em.persist(product);
        return product.getProductId();
    }
}
