package com.lumintra.backend.store;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.character.infrastructure.persistence.CharacterCreationServiceImpl;
import com.lumintra.backend.character.infrastructure.persistence.CharacterLookupServiceImpl;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.commerce.infrastructure.bootstrap.CommerceDataSeeder;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OwnedCharacterRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.device.infrastructure.persistence.LedModelCreationServiceImpl;
import com.lumintra.backend.device.infrastructure.persistence.LedModelLookupServiceImpl;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.store.infrastructure.bootstrap.StoreDataSeeder;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheEvictor;
import com.lumintra.backend.store.infrastructure.persistence.ProductLookupServiceImpl;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seeder_int_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.test-customer-email=customer123@gmail.com"
})
@ActiveProfiles("test")
@Import({
        StoreDataSeeder.class,
        CommerceDataSeeder.class,
        StoreCacheEvictor.class,
        NoOpCacheManager.class,
        ProductLookupServiceImpl.class,
        LedModelCreationServiceImpl.class,
        LedModelLookupServiceImpl.class,
        CharacterCreationServiceImpl.class,
        CharacterLookupServiceImpl.class
})
class StoreAndCommerceSeederIntegrationTest {

    @Autowired
    private StoreDataSeeder storeDataSeeder;

    @Autowired
    private CommerceDataSeeder commerceDataSeeder;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LedModelRepository ledModelRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private CharacterVersionRepository characterVersionRepository;

    @Autowired
    private CharacterLookupService characterLookupService;

    @Autowired
    private BundleRepository bundleRepository;

    @Autowired
    private BundleItemRepository bundleItemRepository;

    @Autowired
    private SubscriptionDetailRepository subscriptionDetailRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private CustomerSubscriptionRepository customerSubscriptionRepository;

    @Autowired
    private OwnedCharacterRepository ownedCharacterRepository;

    @MockitoBean
    private CustomerLookupService customerLookupService;

    @MockitoBean
    private EmployeeLookupService employeeLookupService;

    @Test
    @DisplayName("N: StoreDataSeeder feeds real persisted products, models, and character versions into CommerceDataSeeder idempotently")
    void storeThenCommerceSeeder_populatesLinkedCatalogAndCommerceDataIdempotently() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                42L, "Nguyen Van A", "0901234567", "customer123@gmail.com");
        given(customerLookupService.findProfileByEmail("customer123@gmail.com"))
                .willReturn(Optional.of(customer));
        given(employeeLookupService.findActiveDesigners())
                .willReturn(List.of(new EmployeeProfileSnapshot(77L, "Nhà thiết kế", "designer@lumintra.com")));

        DefaultApplicationArguments args = new DefaultApplicationArguments();
        storeDataSeeder.run(args);
        commerceDataSeeder.run(args);

        // Verify store catalog counts
        assertThat(tagRepository.count()).isEqualTo(12);
        assertThat(productRepository.count()).isEqualTo(19);
        assertThat(ledModelRepository.count()).isEqualTo(3);
        assertThat(characterRepository.count()).isEqualTo(10);
        assertThat(characterRepository.findAll())
                .allSatisfy(character -> assertThat(character.getCreatorEmployeeId()).isEqualTo(77L));
        assertThat(characterVersionRepository.count()).isEqualTo(10);
        assertThat(bundleRepository.count()).isEqualTo(2);
        assertThat(bundleItemRepository.count()).isEqualTo(6);
        assertThat(subscriptionDetailRepository.count()).isEqualTo(4);

        ProductEntity ledPro = productRepository.findFirstByProductName(StoreDataSeeder.PRODUCT_LED_PRO).orElseThrow();
        ProductEntity ledCompact = productRepository.findFirstByProductName(StoreDataSeeder.PRODUCT_LED_COMPACT).orElseThrow();
        ProductEntity charFirst = productRepository.findFirstByProductName(StoreDataSeeder.PRODUCT_CHAR_01).orElseThrow();
        ProductEntity charLast = productRepository.findFirstByProductName(StoreDataSeeder.PRODUCT_CHAR_10).orElseThrow();
        ProductEntity subVip = productRepository.findFirstByProductName(StoreDataSeeder.PRODUCT_SUB_VIP).orElseThrow();
        assertThat(subVip.getBasePrice()).isEqualByComparingTo("999999.00");

        // Verify no shopping cart items are seeded for customers
        assertThat(cartRepository.count()).isZero();
        assertThat(cartItemRepository.count()).isZero();

        // Verify orders & order items reference real seeded products across multiple months
        List<OrderEntity> orders = orderRepository.findAll();
        assertThat(orders).hasSize(37); // 10 lifecycle + 1 webhook + 2 primary warranty + 24 multi-month sales
        assertThat(paymentTransactionRepository.count()).isEqualTo(37);

        Set<Long> allSeededProductIds = productRepository.findAll().stream()
                .map(ProductEntity::getProductId)
                .collect(java.util.stream.Collectors.toSet());
        List<OrderItemEntity> orderItems = orderItemRepository.findAll();
        assertThat(orderItems).isNotEmpty();
        assertThat(orderItems)
                .extracting(OrderItemEntity::getProductId)
                .allMatch(allSeededProductIds::contains);
        // With only primaryCustomer present, Combo 1/2 and their 5 reserved characters are excluded,
        // leaving 3 LEDs + 5 non-reserved characters (+ optional subscriptions) = 8 distinct products.
        assertThat(orderItems.stream().map(OrderItemEntity::getProductId).distinct().count())
                .isGreaterThanOrEqualTo(8);

        // Verify no non-LED item has qty > 1 and no duplicate digital product within completed customer orders
        Set<Long> physicalIds = Set.of(
                ledPro.getProductId(),
                ledCompact.getProductId(),
                productRepository.findFirstByProductName(StoreDataSeeder.PRODUCT_LED_MINI).orElseThrow().getProductId()
        );
        assertThat(orderItems)
                .filteredOn(item -> !physicalIds.contains(item.getProductId()))
                .allSatisfy(item -> assertThat(item.getQuantity()).isEqualTo(1));

        // Verify subscription and owned characters reference real seeded records without duplicates
        List<CustomerSubscriptionEntity> subscriptions = customerSubscriptionRepository.findAll();
        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).getProductId()).isEqualTo(subVip.getProductId());

        Long ownedLastVersionId = characterLookupService.findVersionIdByProductId(charLast.getProductId())
                .orElseThrow();
        List<OwnedCharacterEntity> ownedCharacters = ownedCharacterRepository.findAll();
        assertThat(ownedCharacters).isNotEmpty();
        assertThat(ownedCharacters)
                .extracting(OwnedCharacterEntity::getVersionId)
                .contains(ownedLastVersionId);
        // Combo 1's characters (Heo Hồng Pixel Retro) must remain unowned for primary customer demo
        Long charFirstVersionId = characterLookupService.findVersionIdByProductId(charFirst.getProductId())
                .orElseThrow();
        assertThat(ownedCharacters)
                .filteredOn(oc -> Long.valueOf(42L).equals(oc.getCustomerId()))
                .extracting(OwnedCharacterEntity::getVersionId)
                .doesNotContain(charFirstVersionId);

        // Re-running both seeders must remain strictly idempotent
        long expectedOwnedCount = ownedCharacters.size();
        storeDataSeeder.run(args);
        commerceDataSeeder.run(args);

        assertThat(tagRepository.count()).isEqualTo(12);
        assertThat(productRepository.count()).isEqualTo(19);
        assertThat(ledModelRepository.count()).isEqualTo(3);
        assertThat(characterVersionRepository.count()).isEqualTo(10);
        assertThat(bundleRepository.count()).isEqualTo(2);
        assertThat(bundleItemRepository.count()).isEqualTo(6);
        assertThat(subscriptionDetailRepository.count()).isEqualTo(4);
        assertThat(cartRepository.count()).isZero();
        assertThat(cartItemRepository.count()).isZero();
        assertThat(orderRepository.count()).isEqualTo(37);
        assertThat(paymentTransactionRepository.count()).isEqualTo(37);
        assertThat(customerSubscriptionRepository.count()).isEqualTo(1);
        assertThat(ownedCharacterRepository.count()).isEqualTo(expectedOwnedCount);
    }
}
