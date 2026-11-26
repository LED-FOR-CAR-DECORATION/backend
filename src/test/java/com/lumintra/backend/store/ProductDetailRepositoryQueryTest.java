package com.lumintra.backend.store;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.datasource.url=jdbc:h2:mem:str02_query_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
@ContextConfiguration(classes = ProductDetailRepositoryQueryTest.Config.class)
class ProductDetailRepositoryQueryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {ProductEntity.class, CharacterVersionEntity.class})
    @EnableJpaRepositories(basePackageClasses = {ProductRepository.class, CharacterVersionRepository.class})
    static class Config {}

    @Autowired
    private EntityManager em;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BundleItemRepository bundleItemRepository;

    @Autowired
    private SubscriptionDetailRepository subscriptionDetailRepository;

    @Autowired
    private CharacterVersionRepository characterVersionRepository;

    @Test
    @DisplayName("findWithTagsByProductIdAndStatusIn returns PUBLISHED/ARCHIVED with initialized tags and hides UNPUBLISHED/DELETED")
    void findWithTagsByProductIdAndStatusIn_respectsPublicVisibilityAndInitializesTags() {
        TagEntity tag1 = TagEntity.create("matrix", "LED Hardware");
        TagEntity tag2 = TagEntity.create("cyberpunk", "Neon Style");
        em.persist(tag1);
        em.persist(tag2);

        ProductEntity published = ProductEntity.create(
                "LED Matrix", ProductType.LED, new BigDecimal("200000.00"),
                "desc", "thumb.png", List.of("g1.png"), ProductStatus.PUBLISHED, Set.of(tag1, tag2));
        ProductEntity archived = ProductEntity.create(
                "Old LED", ProductType.LED, new BigDecimal("150000.00"),
                "desc", "thumb.png", List.of(), ProductStatus.ARCHIVED, Set.of(tag1));
        ProductEntity unpublished = ProductEntity.create(
                "Draft LED", ProductType.LED, new BigDecimal("100000.00"),
                "desc", "thumb.png", List.of(), ProductStatus.UNPUBLISHED, Set.of());
        ProductEntity deleted = ProductEntity.create(
                "Deleted LED", ProductType.LED, new BigDecimal("100000.00"),
                "desc", "thumb.png", List.of(), ProductStatus.DELETED, Set.of());

        em.persist(published);
        em.persist(archived);
        em.persist(unpublished);
        em.persist(deleted);
        em.flush();
        em.clear();

        Set<ProductStatus> allowed = EnumSet.of(ProductStatus.PUBLISHED, ProductStatus.ARCHIVED);

        Optional<ProductEntity> foundPublished =
                productRepository.findWithTagsByProductIdAndStatusIn(published.getProductId(), allowed);
        Optional<ProductEntity> foundArchived =
                productRepository.findWithTagsByProductIdAndStatusIn(archived.getProductId(), allowed);
        em.clear();

        assertThat(foundPublished).isPresent();
        assertThat(foundPublished.get().getTags()).extracting(TagEntity::getName)
                .containsExactlyInAnyOrder("matrix", "cyberpunk");
        assertThat(foundArchived).isPresent();
        assertThat(foundArchived.get().getTags()).extracting(TagEntity::getName)
                .containsExactly("matrix");

        assertThat(productRepository.findWithTagsByProductIdAndStatusIn(unpublished.getProductId(), allowed)).isEmpty();
        assertThat(productRepository.findWithTagsByProductIdAndStatusIn(deleted.getProductId(), allowed)).isEmpty();
    }

    @Test
    @DisplayName("CharacterVersionRepository @EntityGraph queries initialize CharacterEntity and BundleItemRepository sorts ascending")
    void characterEntityGraphAndBundleItemOrdering_workWithoutLazyException() {
        CharacterEntity character = CharacterEntity.create(1L, "Cyber Dragon", "Fire animation");
        em.persist(character);

        CharacterVersionEntity v1 = CharacterVersionEntity.create(
                character, 77L, 1, "v1.gif", "v1.bin", false);
        CharacterVersionEntity v2 = CharacterVersionEntity.create(
                character, 77L, 2, "v2.gif", "v2.bin", true);
        em.persist(v1);
        em.persist(v2);

        BundleEntity bundle = BundleEntity.create(
                88L, "Starter Combo", "desc", new BigDecimal("500.00"), new BigDecimal("400.00"),
                "ACTIVE", null, null);
        em.persist(bundle);

        BundleItemEntity item1 = BundleItemEntity.createLedItem(bundle, 5L, 1);
        BundleItemEntity item2 = BundleItemEntity.createCharacterItem(bundle, v2.getVersionId(), 2);
        em.persist(item1);
        em.persist(item2);
        em.flush();
        em.clear();

        Optional<CharacterVersionEntity> currentVersion =
                characterVersionRepository.findWithCharacterFirstByProductIdAndIsCurrentTrue(77L);
        List<CharacterVersionEntity> batchVersions =
                characterVersionRepository.findWithCharacterByVersionIdIn(List.of(v1.getVersionId(), v2.getVersionId()));
        List<BundleItemEntity> items =
                bundleItemRepository.findAllByBundle_BundleIdOrderByBundleItemIdAsc(bundle.getBundleId());
        em.clear();

        assertThat(currentVersion).isPresent();
        assertThat(currentVersion.get().getVersionNumber()).isEqualTo(2);
        assertThat(currentVersion.get().getCharacter().getCharacterName()).isEqualTo("Cyber Dragon");
        assertThat(batchVersions).hasSize(2);
        assertThat(items).extracting(BundleItemEntity::getBundleItemId)
                .containsExactly(item1.getBundleItemId(), item2.getBundleItemId());
    }

    @Test
    @DisplayName("SubscriptionDetailRepository persists multi-feature subscription_detail rows including JSONB features and finds by productId")
    void subscriptionDetailRepository_persistsAndFindsByProductId() {
        ProductEntity vipProduct = ProductEntity.create(
                "Lumintra All-Access VIP Pass",
                ProductType.SUBSCRIPTION,
                new BigDecimal("990000.00"),
                "All characters unlocked forever",
                "vip.png",
                List.of(),
                ProductStatus.PUBLISHED,
                Set.of()
        );
        ProductEntity creatorMonthlyProduct = ProductEntity.create(
                "Creator Studio Monthly",
                ProductType.SUBSCRIPTION,
                new BigDecimal("149000.00"),
                "Extra commission redraws and mobile slots",
                "creator.png",
                List.of(),
                ProductStatus.PUBLISHED,
                Set.of()
        );
        em.persist(vipProduct);
        em.persist(creatorMonthlyProduct);

        SubscriptionDetailEntity vipDetail = SubscriptionDetailEntity.create(
                vipProduct.getProductId(),
                "VIP_LIFETIME",
                "Lumintra All-Access VIP Pass",
                true,
                999, // ignored when isLifetime == true
                true,
                5,
                10,
                Map.of(
                        "commissionPriorityQueue", true,
                        "commissionDiscountPercent", 15,
                        "mobileCloudBackup", true
                ),
                "Unlimited catalog characters, 5 commission revisions, and 10 mobile profiles"
        );
        SubscriptionDetailEntity monthlyDetail = SubscriptionDetailEntity.create(
                creatorMonthlyProduct.getProductId(),
                "CREATOR_30D",
                "Creator Studio Monthly",
                false,
                30,
                false,
                2,
                10,
                Map.of("commissionPriorityQueue", false),
                "2 commission revisions and 10 mobile profiles for 30 days"
        );
        em.persist(vipDetail);
        em.persist(monthlyDetail);
        em.flush();
        em.clear();

        Optional<SubscriptionDetailEntity> foundVip =
                subscriptionDetailRepository.findByProductId(vipProduct.getProductId());
        Optional<SubscriptionDetailEntity> foundMonthly =
                subscriptionDetailRepository.findByProductId(creatorMonthlyProduct.getProductId());
        Optional<SubscriptionDetailEntity> missing =
                subscriptionDetailRepository.findByProductId(999999L);

        assertThat(foundVip).isPresent();
        assertThat(foundVip.get().getSubscriptionDetailId()).isNotNull();
        assertThat(foundVip.get().getProductId()).isEqualTo(vipProduct.getProductId());
        assertThat(foundVip.get().getPlanCode()).isEqualTo("VIP_LIFETIME");
        assertThat(foundVip.get().getPlanName()).isEqualTo("Lumintra All-Access VIP Pass");
        assertThat(foundVip.get().isLifetime()).isTrue();
        assertThat(foundVip.get().getDurationDays()).isNull();
        assertThat(foundVip.get().isAllAccessCharacters()).isTrue();
        assertThat(foundVip.get().getMaxCommissionRevisions()).isEqualTo(5);
        assertThat(foundVip.get().getMaxMobileProfiles()).isEqualTo(10);
        assertThat(foundVip.get().getFeatures())
                .containsEntry("commissionPriorityQueue", true)
                .containsEntry("commissionDiscountPercent", 15)
                .containsEntry("mobileCloudBackup", true);
        assertThat(foundVip.get().getPerksDescription())
                .isEqualTo("Unlimited catalog characters, 5 commission revisions, and 10 mobile profiles");
        assertThat(foundVip.get().getCreatedAt()).isNotNull();

        assertThat(foundMonthly).isPresent();
        assertThat(foundMonthly.get().getPlanCode()).isEqualTo("CREATOR_30D");
        assertThat(foundMonthly.get().isLifetime()).isFalse();
        assertThat(foundMonthly.get().getDurationDays()).isEqualTo(30);
        assertThat(foundMonthly.get().isAllAccessCharacters()).isFalse();
        assertThat(foundMonthly.get().getMaxCommissionRevisions()).isEqualTo(2);
        assertThat(foundMonthly.get().getMaxMobileProfiles()).isEqualTo(10);

        assertThat(missing).isEmpty();
    }

    @Test
    void listsOnlySubscriptionProductsWithPriceStatusAndPlanDetails() {
        var subscription = ProductEntity.create("Membership", ProductType.SUBSCRIPTION,
                new BigDecimal("25.00"), null, null, List.of(), ProductStatus.ARCHIVED, Set.of());
        var led = ProductEntity.create("LED", ProductType.LED, BigDecimal.ONE,
                null, null, List.of(), ProductStatus.PUBLISHED, Set.of());
        em.persist(subscription);
        em.persist(led);
        var detail = SubscriptionDetailEntity.create(subscription.getProductId(), "MONTHLY",
                "Monthly plan", false, 30, true, 0, 3, Map.of(), null);
        em.persist(detail);
        em.persist(SubscriptionDetailEntity.create(led.getProductId(), "OTHER", "Other",
                true, null, false, 0, 3, Map.of(), null));
        em.flush();

        assertThat(subscriptionDetailRepository.listManagedPlans()).singleElement().satisfies(plan -> {
            assertThat(plan.subscriptionDetailId()).isEqualTo(detail.getSubscriptionDetailId());
            assertThat(plan.productId()).isEqualTo(subscription.getProductId());
            assertThat(plan.planCode()).isEqualTo("MONTHLY");
            assertThat(plan.planName()).isEqualTo("Monthly plan");
            assertThat(plan.basePrice()).isEqualByComparingTo("25.00");
            assertThat(plan.lifetime()).isFalse();
            assertThat(plan.durationDays()).isEqualTo(30);
            assertThat(plan.allAccessCharacters()).isTrue();
            assertThat(plan.status()).isEqualTo(ProductStatus.ARCHIVED);
            assertThat(plan.createdAt()).isNotNull();
        });
    }
}
