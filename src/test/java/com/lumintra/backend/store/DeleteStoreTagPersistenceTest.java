package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.store.application.DeleteStoreTagUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.caching.StoreTagUpdatedCacheListener;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagDeletionRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
@ContextConfiguration(classes = DeleteStoreTagPersistenceTest.Config.class)
class DeleteStoreTagPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ProductEntity.class)
    @EnableJpaRepositories(basePackageClasses = ProductRepository.class)
    @Import({
        DeleteStoreTagUseCase.class,
        StoreTagUpdatedCacheListener.class,
        TagDeletionRepository.class
    })
    static class Config {}

    @Autowired EntityManager em;
    @Autowired DeleteStoreTagUseCase useCase;
    @MockitoBean CacheManager caches;

    @Autowired ProductRepository products;

    @Test
    void detachesSharedTagWithoutDeletingProductsOrOtherTags() {
        var deleted = TagEntity.create("Animals", null);
        var retained = TagEntity.create("Space", null);
        em.persist(deleted);
        em.persist(retained);
        long first = addProduct("First", Set.of(deleted, retained));
        long second = addProduct("Second", Set.of(deleted));
        long tagId = deleted.getTagId();
        em.flush();
        em.clear();

        useCase.execute(tagId);
        em.flush();
        em.clear();

        assertThat(em.find(TagEntity.class, tagId)).isNull();
        assertThat(em.find(ProductEntity.class, first).getTags())
                .extracting(TagEntity::getTagId)
                .containsExactly(retained.getTagId());
        assertThat(em.find(ProductEntity.class, second).getTags()).isEmpty();

        var tagCache = mock(Cache.class);
        var listCache = mock(Cache.class);
        var detailCache = mock(Cache.class);
        when(caches.getCache("store:tags")).thenReturn(tagCache);
        when(caches.getCache("store:products")).thenReturn(listCache);
        when(caches.getCache("store:product")).thenReturn(detailCache);
        verifyNoInteractions(caches);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        verify(tagCache).clear();
        verify(listCache).clear();
        verify(detailCache).clear();
    }

    @Test
    void rejectsMissingTagWithoutEviction() {
        assertThatThrownBy(() -> useCase.execute(Long.MAX_VALUE))
                .isInstanceOf(StoreProductTagNotFoundException.class);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        verifyNoInteractions(caches);
    }

    private long addProduct(String name, Set<TagEntity> tags) {
        var product =
                ProductEntity.create(
                        name,
                        ProductType.CHARACTER,
                        BigDecimal.TEN,
                        null,
                        null,
                        List.of(),
                        ProductStatus.PUBLISHED,
                        tags);
        em.persist(product);
        return product.getProductId();
    }
}
