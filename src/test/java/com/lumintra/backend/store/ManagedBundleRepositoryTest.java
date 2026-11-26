package com.lumintra.backend.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ManagedBundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:str02_query_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = ManagedBundleRepositoryTest.Config.class)
class ManagedBundleRepositoryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, CharacterVersionEntity.class})
    @EnableJpaRepositories(
            basePackageClasses = {ProductRepository.class, CharacterVersionRepository.class})
    static class Config {}

    @Autowired private EntityManager em;

    @Autowired private ManagedBundleRepository bundles;

    @Autowired private BundleItemRepository bundleItemRepository;

    @Autowired private SubscriptionDetailRepository subscriptionDetailRepository;

    @Autowired private CharacterVersionRepository characterVersionRepository;

    @Test
    void paginatesAllStatusesAndCountsItemEntriesIncludingEmptyBundles() {
        var published =
                BundleEntity.create(1L, "Published", null, null, null, "PUBLISHED", null, null);
        var deleted = BundleEntity.create(2L, "Deleted", null, null, null, "DELETED", null, null);
        em.persist(published);
        em.persist(deleted);
        em.persist(BundleItemEntity.createLedItem(published, 1L, 3));
        em.persist(BundleItemEntity.createCharacterItem(published, 2L, 1));
        em.flush();

        var first = bundles.list(null, PageRequest.of(0, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent().getFirst().status()).isEqualTo("DELETED");
        assertThat(first.getContent().getFirst().itemCount()).isZero();
        var filtered = bundles.list("PUBLISHED", PageRequest.of(0, 1));
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getContent().getFirst().itemCount()).isEqualTo(2);
    }
}
