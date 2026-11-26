package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.shared.exception.store.BundleNotFoundException;
import com.lumintra.backend.store.application.DeleteBundleUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.caching.StoreProductUpdatedCacheListener;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
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
@ContextConfiguration(classes = DeleteBundlePersistenceTest.Config.class)
class DeleteBundlePersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, OrderEntity.class})
    @EnableJpaRepositories(
            basePackageClasses = {ProductRepository.class, OrderItemRepository.class})
    @Import({DeleteBundleUseCase.class, StoreProductUpdatedCacheListener.class})
    static class Config {}

    @Autowired EntityManager em;
    @Autowired DeleteBundleUseCase useCase;
    @MockitoBean StoreCacheEvictor cacheEvictor;

    @Autowired ProductRepository products;

    @Autowired BundleRepository bundles;

    @Test
    void archivesBundleWithOrderHistoryAndPreservesPrice() {
        var bundle = insertBundle();
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
        var item = OrderItemEntity.create(order, bundle.getProductId(), 1, price);
        em.persist(item);
        useCase.execute(bundle.getBundleId());
        em.flush();
        em.clear();

        assertThat(em.find(BundleEntity.class, bundle.getBundleId()).getStatus())
                .isEqualTo("ARCHIVED");
        assertThat(em.find(ProductEntity.class, bundle.getProductId()).getStatus())
                .isEqualTo(ProductStatus.ARCHIVED);
        assertThat(em.find(OrderItemEntity.class, item.getOrderItemId()).getUnitPrice())
                .isEqualByComparingTo(price);
        verifyNoInteractions(cacheEvictor);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(cacheEvictor).evictProductAndAllProducts(bundle.getProductId());
    }

    @Test
    void softDeletesBothRowsWhenNoOrderReferencesBundle() {
        var bundle = insertBundle();
        useCase.execute(bundle.getBundleId());
        em.flush();
        em.clear();
        assertThat(em.find(BundleEntity.class, bundle.getBundleId()).getStatus())
                .isEqualTo("DELETED");
        assertThat(em.find(ProductEntity.class, bundle.getProductId()).getStatus())
                .isEqualTo(ProductStatus.DELETED);
    }

    @Test
    void rejectsMissingBundle() {
        assertThatThrownBy(() -> useCase.execute(Long.MAX_VALUE))
                .isInstanceOf(BundleNotFoundException.class);
        verifyNoInteractions(cacheEvictor);
    }

    private BundleEntity insertBundle() {
        var product =
                ProductEntity.create(
                        "Bundle",
                        ProductType.BUNDLE,
                        BigDecimal.TEN,
                        null,
                        null,
                        List.of(),
                        ProductStatus.PUBLISHED,
                        Set.of());
        em.persist(product);
        return bundles.save(
                BundleEntity.create(
                        product.getProductId(),
                        "Bundle",
                        null,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        "PUBLISHED",
                        null,
                        null));
    }
}
