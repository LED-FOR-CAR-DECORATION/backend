package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.store.api.request.UpdateStoreProductRequest;
import com.lumintra.backend.store.application.UpdateStoreProductUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.caching.StoreProductUpdatedCacheListener;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@DataJpaTest(
        properties = {
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = UpdateStoreProductPersistenceTest.Config.class)
class UpdateStoreProductPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, OrderEntity.class})
    @EnableJpaRepositories(basePackageClasses = ProductRepository.class)
    @Import({UpdateStoreProductUseCase.class, StoreProductUpdatedCacheListener.class})
    static class Config {}

    @Autowired EntityManager em;
    @Autowired UpdateStoreProductUseCase useCase;
    @MockitoBean StoreCacheEvictor cacheEvictor;

    @Test
    void updatesProductAndTagsWithoutChangingHistoricalOrderPrices() {
        var previous = TagEntity.create("previous", "Previous");
        var replacement = TagEntity.create("replacement", "Replacement");
        em.persist(previous);
        em.persist(replacement);
        var product = insertProduct(ProductStatus.PUBLISHED, Set.of(previous));
        var oldPrice = product.getBasePrice();
        var order =
                OrderEntity.createPendingPayment(
                        1L,
                        null,
                        oldPrice,
                        BigDecimal.ZERO,
                        oldPrice,
                        OrderType.DIGITAL,
                        Instant.now().plusSeconds(600));
        em.persist(order);
        var item = OrderItemEntity.create(order, product.getProductId(), 1, oldPrice);
        em.persist(item);
        em.flush();
        em.clear();

        long replacementId = replacement.getTagId();
        var response =
                useCase.execute(
                        product.getProductId(),
                        new UpdateStoreProductRequest(
                                "Updated product",
                                new BigDecimal("90.50"),
                                "New description",
                                "https://example.com/thumb.png",
                                List.of("https://example.com/gallery.png"),
                                List.of(replacementId, replacementId)));
        em.flush();
        em.clear();

        var saved = em.find(ProductEntity.class, product.getProductId());
        assertThat(saved.getProductName()).isEqualTo("Updated product");
        assertThat(saved.getBasePrice()).isEqualByComparingTo("90.50");
        assertThat(saved.getDescription()).isEqualTo("New description");
        assertThat(saved.getThumbnailUrl()).isEqualTo("https://example.com/thumb.png");
        assertThat(saved.getGalleryUrls()).containsExactly("https://example.com/gallery.png");
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(saved.getProductType()).isEqualTo(ProductType.CHARACTER);
        assertThat(saved.getTags()).extracting(TagEntity::getTagId).containsExactly(replacementId);
        assertThat(response.tagIds()).containsExactly(replacementId);
        assertThat(em.find(OrderItemEntity.class, item.getOrderItemId()).getUnitPrice())
                .isEqualByComparingTo(oldPrice);
        verifyNoInteractions(cacheEvictor);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(cacheEvictor).evictProductAndAllProducts(product.getProductId());
    }

    @Test
    void preservesOmittedTagsAndClearsExplicitEmptyTagsWithoutEvictingOnRollback() {
        var tag = TagEntity.create("keep", "Keep");
        em.persist(tag);
        var product = insertProduct(ProductStatus.UNPUBLISHED, Set.of(tag));
        long id = product.getProductId();

        assertThat(useCase.execute(id, request(null)).tagIds()).containsExactly(tag.getTagId());
        assertThat(useCase.execute(id, request(List.of())).tagIds()).isEmpty();
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verifyNoInteractions(cacheEvictor);
    }

    @Test
    void rejectsMissingAndDeletedProductsAndInvalidTags() {
        var deleted = insertProduct(ProductStatus.DELETED, Set.of());
        var active = insertProduct(ProductStatus.PUBLISHED, Set.of());
        assertThatThrownBy(() -> useCase.execute(Long.MAX_VALUE, request(null)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute(deleted.getProductId(), request(null)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        active.getProductId(), request(List.of(Long.MAX_VALUE))))
                .isInstanceOf(StoreProductTagNotFoundException.class);
        assertThat(active.getProductName()).isEqualTo("Original");
        verifyNoInteractions(cacheEvictor);
    }

    private ProductEntity insertProduct(ProductStatus status, Set<TagEntity> tags) {
        var product =
                ProductEntity.create(
                        "Original",
                        ProductType.CHARACTER,
                        new BigDecimal("20.00"),
                        null,
                        null,
                        List.of(),
                        status,
                        new HashSet<>(tags));
        em.persist(product);
        return product;
    }

    private UpdateStoreProductRequest request(List<Long> tagIds) {
        return new UpdateStoreProductRequest("Updated", BigDecimal.ZERO, null, null, null, tagIds);
    }
}
