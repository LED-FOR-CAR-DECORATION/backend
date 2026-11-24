package com.lumintra.backend.commerce;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.infrastructure.bootstrap.CommerceDataSeeder;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OwnedCharacterRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CommerceDataSeederTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private CustomerSubscriptionRepository customerSubscriptionRepository;

    @Mock
    private OwnedCharacterRepository ownedCharacterRepository;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private CharacterLookupService characterLookupService;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private CommerceDataSeeder seeder;

    @Captor
    private ArgumentCaptor<OrderEntity> orderCaptor;

    @Captor
    private ArgumentCaptor<OrderItemEntity> orderItemCaptor;

    @Captor
    private ArgumentCaptor<OrderDeliveryEntity> deliveryCaptor;

    @Captor
    private ArgumentCaptor<PaymentTransactionEntity> paymentCaptor;

    @Captor
    private ArgumentCaptor<CartItemEntity> cartItemCaptor;

    @Captor
    private ArgumentCaptor<OwnedCharacterEntity> ownedCharacterCaptor;

    @Captor
    private ArgumentCaptor<CustomerSubscriptionEntity> subscriptionCaptor;

    private static final String TEST_EMAIL = "customer123@gmail.com";
    private static final Long TEST_CUSTOMER_ID = 10L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seeder, "customerEmail", TEST_EMAIL);
    }

    @Test
    @DisplayName("N: Successful seed when database has no commerce data creates cart, 13 primary orders, and digital ownership")
    void run_cleanDatabase_seedsAllCommerceDataSuccessfully() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Nguyen Van A", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(customer));

        ProductSnapshot physical = new ProductSnapshot(
                101L, "Lumintra LED Matrix", ProductType.LED, "thumb1", new BigDecimal("300000.00"), ProductStatus.PUBLISHED);
        ProductSnapshot digital = new ProductSnapshot(
                202L, "Cyberpunk Cat", ProductType.CHARACTER, "thumb2", new BigDecimal("60000.00"), ProductStatus.PUBLISHED);
        ProductSnapshot archivedChar = new ProductSnapshot(
                203L, "Classic Mascot", ProductType.CHARACTER, "thumb2b", new BigDecimal("40000.00"), ProductStatus.ARCHIVED);
        ProductSnapshot subscription = new ProductSnapshot(
                303L, "Lumintra Pro", ProductType.SUBSCRIPTION, "thumb3", new BigDecimal("120000.00"), ProductStatus.PUBLISHED);

        given(productLookupService.findAllSnapshots()).willReturn(List.of(physical, digital, archivedChar, subscription));
        given(characterLookupService.findVersionIdByProductId(203L)).willReturn(Optional.of(505L));

        CartEntity cart = CartEntity.create(TEST_CUSTOMER_ID);
        ReflectionTestUtils.setField(cart, "cartId", 1L);
        given(cartRepository.findByCustomerId(TEST_CUSTOMER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(1L)).willReturn(Collections.emptyList());

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(true);

        given(orderRepository.save(any(OrderEntity.class))).willAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "orderId", 100L);
            return o;
        });

        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(TEST_CUSTOMER_ID, "ACTIVE")).willReturn(false);
        given(ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(TEST_CUSTOMER_ID, 505L)).willReturn(false);

        seeder.run(applicationArguments);

        // Verify cart items
        verify(cartItemRepository, times(3)).save(cartItemCaptor.capture());
        List<CartItemEntity> savedCartItems = cartItemCaptor.getAllValues();
        assertThat(savedCartItems).hasSize(3);
        assertThat(savedCartItems.get(0).getProductId()).isEqualTo(101L);
        assertThat(savedCartItems.get(0).getUnitPrice()).isEqualByComparingTo("300000.00");
        assertThat(savedCartItems.get(1).getProductId()).isEqualTo(202L);
        assertThat(savedCartItems.get(1).getUnitPrice()).isEqualByComparingTo("60000.00");
        assertThat(savedCartItems.get(2).getProductId()).isEqualTo(303L);
        assertThat(savedCartItems.get(2).getUnitPrice()).isEqualByComparingTo("120000.00");

        // Verify 13 orders (10 lifecycle + 1 webhook + 2 primary customer warranty orders)
        verify(orderRepository, times(13)).save(orderCaptor.capture());
        List<OrderEntity> orders = orderCaptor.getAllValues();
        assertThat(orders).hasSize(13);

        // Check key orders:
        // Order 1: PENDING_PAYMENT (active, expires in future)
        assertThat(orders.get(0).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orders.get(0).getOrderType()).isEqualTo(OrderType.PHYSICAL);
        assertThat(orders.get(0).getExpiresAt()).isAfter(Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));

        // Order 2: PROCESSING
        assertThat(orders.get(1).getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(orders.get(1).getOrderType()).isEqualTo(OrderType.PHYSICAL);

        // Order 3: PARTIALLY_FULFILLED
        assertThat(orders.get(2).getStatus()).isEqualTo(OrderStatus.PARTIALLY_FULFILLED);
        assertThat(orders.get(2).getOrderType()).isEqualTo(OrderType.MIXED);

        // Order 4: SHIPPING
        assertThat(orders.get(3).getStatus()).isEqualTo(OrderStatus.SHIPPING);

        // Order 5: DELIVERED
        assertThat(orders.get(4).getStatus()).isEqualTo(OrderStatus.DELIVERED);

        // Order 6: DELIVERY_FAILED
        assertThat(orders.get(5).getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);

        // Order 7: COMPLETED
        assertThat(orders.get(6).getStatus()).isEqualTo(OrderStatus.COMPLETED);

        // Order 8: CANCELED
        assertThat(orders.get(7).getStatus()).isEqualTo(OrderStatus.CANCELED);

        // Order 9: DIGITAL (COMPLETED)
        assertThat(orders.get(8).getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orders.get(8).getOrderType()).isEqualTo(OrderType.DIGITAL);

        // Order 10: EXPIRED PENDING_PAYMENT
        assertThat(orders.get(9).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orders.get(9).getExpiresAt()).isBefore(Instant.now());

        // Order 11: 10,000 VND PENDING_PAYMENT webhook test order
        assertThat(orders.get(10).getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orders.get(10).getOrderType()).isEqualTo(OrderType.PHYSICAL);
        assertThat(orders.get(10).getTotalAmount()).isEqualByComparingTo("10000.00");
        assertThat(orders.get(10).getExpiresAt()).isAfter(Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));

        // Verify deliveries: exactly 12 deliveries (order 9 has no delivery)
        verify(orderDeliveryRepository, times(12)).save(deliveryCaptor.capture());
        List<OrderDeliveryEntity> deliveries = deliveryCaptor.getAllValues();
        assertThat(deliveries).hasSize(12);

        // Order 4 delivery: SHIPPING, tracking AHA-LMT-TRACK-8888
        OrderDeliveryEntity shippingDelivery = deliveries.get(3);
        assertThat(shippingDelivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(shippingDelivery.getTrackingNumber()).isEqualTo("AHA-LMT-TRACK-8888");

        // Order 5 delivery: DELIVERED, tracking AHA-LMT-TRACK-9999
        OrderDeliveryEntity deliveredDelivery = deliveries.get(4);
        assertThat(deliveredDelivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(deliveredDelivery.getTrackingNumber()).isEqualTo("AHA-LMT-TRACK-9999");

        // Order 6 delivery: FAILED
        OrderDeliveryEntity failedDelivery = deliveries.get(5);
        assertThat(failedDelivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(failedDelivery.getTrackingNumber()).isEqualTo("AHA-LMT-TRACK-FAIL-01");

        // Verify payments: 13 transactions
        verify(paymentTransactionRepository, times(13)).save(paymentCaptor.capture());
        List<PaymentTransactionEntity> payments = paymentCaptor.getAllValues();
        assertThat(payments).hasSize(13);
        assertThat(payments.get(0).getTransactionCode()).isEqualTo("LMTORD100SEED0001");
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.get(1).getTransactionCode()).isEqualTo("LMTORD100SEED0002");
        assertThat(payments.get(1).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payments.get(7).getTransactionCode()).isEqualTo("LMTORD100SEED0008");
        assertThat(payments.get(7).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payments.get(9).getTransactionCode()).isEqualTo("LMTORD100SEED0010");
        assertThat(payments.get(9).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.get(10).getTransactionCode()).isEqualTo("LMTORD100SEED0011");
        assertThat(payments.get(10).getAmount()).isEqualByComparingTo("10000.00");
        assertThat(payments.get(10).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.get(11).getTransactionCode()).isEqualTo("LMTORD100SEED0012");
        assertThat(payments.get(12).getTransactionCode()).isEqualTo("LMTORD100SEED0013");

        // Verify order items reference real seeded products (15 items across 13 orders)
        verify(orderItemRepository, times(15)).save(orderItemCaptor.capture());
        List<OrderItemEntity> orderItems = orderItemCaptor.getAllValues();
        assertThat(orderItems).allSatisfy(item ->
                assertThat(item.getProductId()).isIn(101L, 202L));

        // Verify digital ownership and subscription reference real seeded entities
        verify(customerSubscriptionRepository).save(subscriptionCaptor.capture());
        assertThat(subscriptionCaptor.getValue().getProductId()).isEqualTo(303L);
        verify(ownedCharacterRepository).save(ownedCharacterCaptor.capture());
        assertThat(ownedCharacterCaptor.getValue().getVersionId()).isEqualTo(505L);
    }

    @Test
    @DisplayName("B: When legacy LMT-SEED-TXN-* marker exists, migrates codes to LMTORD format without duplicating orders")
    void run_legacySeedMarkerExists_migratesCodesWithoutDuplicatingOrders() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Nguyen Van A", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(customer));
        given(productLookupService.findAllSnapshots()).willReturn(defaultSeedProducts());

        CartEntity cart = CartEntity.create(TEST_CUSTOMER_ID);
        given(cartRepository.findByCustomerId(TEST_CUSTOMER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(any())).willReturn(List.of(
                CartItemEntity.create(cart, 101L, 1, new BigDecimal("250000.00"))));

        PaymentTransactionEntity legacy1 = PaymentTransactionEntity.of(
                1L, 1L, PaymentGateway.SEPAY, "LMT-SEED-TXN-001", new BigDecimal("250000.00"), PaymentStatus.PENDING);
        PaymentTransactionEntity legacy10 = PaymentTransactionEntity.of(
                10L, 10L, PaymentGateway.SEPAY, "LMT-SEED-TXN-010", new BigDecimal("250000.00"), PaymentStatus.PENDING);

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(true);
        given(paymentTransactionRepository.findByTransactionCodeStartingWith("LMT-SEED-TXN-"))
                .willReturn(List.of(legacy1, legacy10));
        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(TEST_CUSTOMER_ID, "ACTIVE")).willReturn(true);

        seeder.run(applicationArguments);

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(paymentTransactionRepository).saveAll(List.of(legacy1, legacy10));
        assertThat(legacy1.getTransactionCode()).isEqualTo("LMTORD1SEED0001");
        assertThat(legacy10.getTransactionCode()).isEqualTo("LMTORD10SEED0010");
    }

    @Test
    @DisplayName("B: When orders are already seeded, order creation is skipped idempotently")
    void run_ordersAlreadySeeded_skipsOrderGeneration() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Nguyen Van A", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(customer));
        given(productLookupService.findAllSnapshots()).willReturn(defaultSeedProducts());

        CartEntity cart = CartEntity.create(TEST_CUSTOMER_ID);
        given(cartRepository.findByCustomerId(TEST_CUSTOMER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(any())).willReturn(List.of(
                CartItemEntity.create(cart, 101L, 1, new BigDecimal("250000.00"))));

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0001")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(true);
        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(TEST_CUSTOMER_ID, "ACTIVE")).willReturn(true);

        seeder.run(applicationArguments);

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(orderDeliveryRepository, never()).save(any(OrderDeliveryEntity.class));
        verify(paymentTransactionRepository, never()).save(any(PaymentTransactionEntity.class));
        verify(customerSubscriptionRepository, never()).save(any(CustomerSubscriptionEntity.class));
        verify(ownedCharacterRepository, never()).save(any(OwnedCharacterEntity.class));
    }

    @Test
    @DisplayName("B: When cart already has items, cart item seeding is skipped")
    void run_cartHasItems_skipsCartItemSeeding() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Nguyen Van A", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(customer));
        given(productLookupService.findAllSnapshots()).willReturn(defaultSeedProducts());

        CartEntity cart = CartEntity.create(TEST_CUSTOMER_ID);
        ReflectionTestUtils.setField(cart, "cartId", 1L);
        given(cartRepository.findByCustomerId(TEST_CUSTOMER_ID)).willReturn(Optional.of(cart));

        CartItemEntity existing = CartItemEntity.create(cart, 101L, 2, new BigDecimal("250000.00"));
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(1L)).willReturn(List.of(existing));

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0001")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(true);
        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(TEST_CUSTOMER_ID, "ACTIVE")).willReturn(true);

        seeder.run(applicationArguments);

        verify(cartItemRepository, never()).save(any(CartItemEntity.class));
    }

    @Test
    @DisplayName("A: When no customer found in database, skips seeding gracefully without error")
    void run_noCustomerFound_skipsSeedingSafely() {
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.empty());
        given(customerLookupService.findFirstProfile()).willReturn(Optional.empty());

        seeder.run(applicationArguments);

        verifyNoInteractions(cartRepository);
        verifyNoInteractions(cartItemRepository);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderDeliveryRepository);
        verifyNoInteractions(paymentTransactionRepository);
    }

    @Test
    @DisplayName("N: When customer not found by email, falls back to first customer profile")
    void run_customerFoundByFallback_seedsForFallbackCustomer() {
        CustomerProfileSnapshot fallbackCustomer = new CustomerProfileSnapshot(
                99L, "Fallback Customer", "0999999999", "fallback@example.com");
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.empty());
        given(customerLookupService.findFirstProfile()).willReturn(Optional.of(fallbackCustomer));
        given(productLookupService.findAllSnapshots()).willReturn(defaultSeedProducts());

        CartEntity cart = CartEntity.create(99L);
        ReflectionTestUtils.setField(cart, "cartId", 5L);
        given(cartRepository.findByCustomerId(99L)).willReturn(Optional.of(cart));
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(5L)).willReturn(Collections.emptyList());

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0001")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(true);
        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(99L, "ACTIVE")).willReturn(true);

        seeder.run(applicationArguments);

        verify(cartItemRepository, times(3)).save(any(CartItemEntity.class));
    }

    @Test
    @DisplayName("A: When store has no seeded products, skips commerce seeding instead of using fake fallback IDs")
    void run_storeHasNoProducts_skipsSeedingSafely() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Nguyen Van A", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(customer));
        given(productLookupService.findAllSnapshots()).willReturn(Collections.emptyList());

        seeder.run(applicationArguments);

        verifyNoInteractions(cartRepository);
        verifyNoInteractions(cartItemRepository);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderItemRepository);
        verifyNoInteractions(orderDeliveryRepository);
        verifyNoInteractions(paymentTransactionRepository);
        verifyNoInteractions(customerSubscriptionRepository);
        verifyNoInteractions(ownedCharacterRepository);
    }

    @Test
    @DisplayName("B: When only partial store product types exist, skips commerce seeding safely")
    void run_partialStoreProductsMissingRequiredTypes_skipsSeedingSafely() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Nguyen Van A", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(customer));
        given(productLookupService.findAllSnapshots()).willReturn(List.of(
                new ProductSnapshot(101L, "LED Only", ProductType.LED, "thumb", new BigDecimal("250000.00"), ProductStatus.PUBLISHED)
        ));

        seeder.run(applicationArguments);

        verifyNoInteractions(cartRepository);
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("N: Published products take priority over unpublished products")
    void run_prefersPublishedProductsOverUnpublished() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Nguyen Van A", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(customer));

        ProductSnapshot unpubLed = new ProductSnapshot(
                10L, "Draft LED", ProductType.LED, "thumb", new BigDecimal("100000.00"), ProductStatus.UNPUBLISHED);
        ProductSnapshot pubLed = new ProductSnapshot(
                20L, "Published LED", ProductType.LED, "thumb", new BigDecimal("200000.00"), ProductStatus.PUBLISHED);
        ProductSnapshot digital = new ProductSnapshot(
                30L, "Published Character", ProductType.CHARACTER, "thumb2", new BigDecimal("50000.00"), ProductStatus.PUBLISHED);
        ProductSnapshot subscription = new ProductSnapshot(
                40L, "Published Subscription", ProductType.SUBSCRIPTION, "thumb3", new BigDecimal("99000.00"), ProductStatus.PUBLISHED);

        given(productLookupService.findAllSnapshots()).willReturn(List.of(unpubLed, pubLed, digital, subscription));

        given(cartRepository.findByCustomerId(TEST_CUSTOMER_ID)).willReturn(Optional.empty());
        CartEntity newCart = CartEntity.create(TEST_CUSTOMER_ID);
        ReflectionTestUtils.setField(newCart, "cartId", 3L);
        given(cartRepository.save(any(CartEntity.class))).willReturn(newCart);
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(3L)).willReturn(Collections.emptyList());

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0001")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(true);
        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(TEST_CUSTOMER_ID, "ACTIVE")).willReturn(true);

        seeder.run(applicationArguments);

        verify(cartItemRepository, times(3)).save(cartItemCaptor.capture());
        List<CartItemEntity> items = cartItemCaptor.getAllValues();
        // Should select pubLed (ID 20) instead of unpubLed (ID 10)
        assertThat(items.get(0).getProductId()).isEqualTo(20L);
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("200000.00");
        verify(ownedCharacterRepository, never()).save(any(OwnedCharacterEntity.class));
    }

    @Test
    @DisplayName("N: When SEED0001..0010 already exist but SEED0011 does not, seeds only the 10,000 VND webhook test order")
    void run_existingOrdersWithoutWebhookTestOrder_seedsOnlyWebhookTestOrder() {
        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Nguyen Van A", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(customer));
        given(productLookupService.findAllSnapshots()).willReturn(defaultSeedProducts());

        CartEntity cart = CartEntity.create(TEST_CUSTOMER_ID);
        ReflectionTestUtils.setField(cart, "cartId", 4L);
        given(cartRepository.findByCustomerId(TEST_CUSTOMER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(4L)).willReturn(List.of(
                CartItemEntity.create(cart, 101L, 1, new BigDecimal("250000.00"))));

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0001")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(true);
        given(orderRepository.save(any(OrderEntity.class))).willAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "orderId", 111L);
            return o;
        });
        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(TEST_CUSTOMER_ID, "ACTIVE")).willReturn(true);

        seeder.run(applicationArguments);

        verify(orderRepository, times(1)).save(orderCaptor.capture());
        OrderEntity seededOrder = orderCaptor.getValue();
        assertThat(seededOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(seededOrder.getTotalAmount()).isEqualByComparingTo("10000.00");

        verify(orderItemRepository, times(1)).save(orderItemCaptor.capture());
        assertThat(orderItemCaptor.getValue().getProductId()).isEqualTo(101L);

        verify(paymentTransactionRepository, times(1)).save(paymentCaptor.capture());
        PaymentTransactionEntity seededPayment = paymentCaptor.getValue();
        assertThat(seededPayment.getTransactionCode()).isEqualTo("LMTORD111SEED0011");
        assertThat(seededPayment.getAmount()).isEqualByComparingTo("10000.00");
        assertThat(seededPayment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("N: When SEED0012 is absent and extra customers exist, seeds 7 completed warranty orders across all customers")
    void run_extraCustomerProfilesPresent_seedsWarrantyOrdersAndItemsAcrossCustomers() {
        CustomerProfileSnapshot primary = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Test Customer", "0901234567", TEST_EMAIL);
        CustomerProfileSnapshot c2 = new CustomerProfileSnapshot(
                20L, "Nguyễn Minh Anh", "0902345678", "minhanh.nguyen@gmail.com");
        CustomerProfileSnapshot c3 = new CustomerProfileSnapshot(
                30L, "Trần Quốc Bảo", "0903456789", "quocbao.tran@gmail.com");
        CustomerProfileSnapshot c4 = new CustomerProfileSnapshot(
                40L, "Lê Thảo Vy", "0904567890", "thaovy.le@gmail.com");

        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(primary));
        given(customerLookupService.findProfileByEmail("minhanh.nguyen@gmail.com")).willReturn(Optional.of(c2));
        given(customerLookupService.findProfileByEmail("quocbao.tran@gmail.com")).willReturn(Optional.of(c3));
        given(customerLookupService.findProfileByEmail("thaovy.le@gmail.com")).willReturn(Optional.of(c4));

        ProductSnapshot ledCompact = new ProductSnapshot(
                102L, "Lumintra Matrix 32x64 Compact", ProductType.LED, "thumb1b",
                new BigDecimal("190000.00"), ProductStatus.PUBLISHED);
        List<ProductSnapshot> products = new java.util.ArrayList<>(defaultSeedProducts());
        products.add(ledCompact);
        given(productLookupService.findAllSnapshots()).willReturn(products);

        CartEntity cart = CartEntity.create(TEST_CUSTOMER_ID);
        ReflectionTestUtils.setField(cart, "cartId", 6L);
        given(cartRepository.findByCustomerId(TEST_CUSTOMER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(6L)).willReturn(List.of(
                CartItemEntity.create(cart, 101L, 1, new BigDecimal("250000.00"))));

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0001")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(true);
        given(orderRepository.save(any(OrderEntity.class))).willAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "orderId", 200L);
            return o;
        });
        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(TEST_CUSTOMER_ID, "ACTIVE")).willReturn(true);

        seeder.run(applicationArguments);

        verify(orderRepository, times(7)).save(orderCaptor.capture());
        List<OrderEntity> warrantyOrders = orderCaptor.getAllValues();
        assertThat(warrantyOrders)
                .extracting(OrderEntity::getCustomerId)
                .containsExactly(TEST_CUSTOMER_ID, TEST_CUSTOMER_ID, 20L, 20L, 30L, 40L, 40L);
        assertThat(warrantyOrders)
                .allSatisfy(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED));

        // 2 MIXED orders (2 items each = 4) + 5 PHYSICAL orders (1 item each = 5) = 9 order items
        verify(orderItemRepository, times(9)).save(orderItemCaptor.capture());
        assertThat(orderItemCaptor.getAllValues())
                .extracting(OrderItemEntity::getProductId)
                .contains(101L, 102L, 202L);
    }

    @Test
    @DisplayName("N: When SALES001 is absent, seeds 24 completed multi-month sales orders across LED, characters, bundle, and subscription")
    void run_salesHistoryAbsent_seedsMultiMonthOrdersAcrossCatalogProducts() {
        CustomerProfileSnapshot primary = new CustomerProfileSnapshot(
                TEST_CUSTOMER_ID, "Test Customer", "0901234567", TEST_EMAIL);
        given(customerLookupService.findProfileByEmail(TEST_EMAIL)).willReturn(Optional.of(primary));

        List<ProductSnapshot> catalog = List.of(
                new ProductSnapshot(101L, "Lumintra Matrix 64x64 Pro", ProductType.LED, "thumb1",
                        new BigDecimal("250000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(102L, "Lumintra Matrix 32x64 Compact", ProductType.LED, "thumb1b",
                        new BigDecimal("190000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(201L, "Char 1", ProductType.CHARACTER, "c1",
                        new BigDecimal("49000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(202L, "Char 2", ProductType.CHARACTER, "c2",
                        new BigDecimal("59000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(203L, "Char 3", ProductType.CHARACTER, "c3",
                        new BigDecimal("69000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(204L, "Char 4", ProductType.CHARACTER, "c4",
                        new BigDecimal("79000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(205L, "Char 5", ProductType.CHARACTER, "c5",
                        new BigDecimal("89000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(301L, "Lumintra VIP Pass", ProductType.SUBSCRIPTION, "s1",
                        new BigDecimal("99000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(401L, "Starter Neon Combo", ProductType.BUNDLE, "b1",
                        new BigDecimal("299000.00"), ProductStatus.PUBLISHED)
        );
        given(productLookupService.findAllSnapshots()).willReturn(catalog);

        CartEntity cart = CartEntity.create(TEST_CUSTOMER_ID);
        ReflectionTestUtils.setField(cart, "cartId", 7L);
        given(cartRepository.findByCustomerId(TEST_CUSTOMER_ID)).willReturn(Optional.of(cart));
        given(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(7L)).willReturn(List.of(
                CartItemEntity.create(cart, 101L, 1, new BigDecimal("250000.00"))));

        given(paymentTransactionRepository.existsByTransactionCode("LMT-SEED-TXN-001")).willReturn(false);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0001")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0011")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SEED0012")).willReturn(true);
        given(paymentTransactionRepository.existsByTransactionCodeEndingWith("SALES001")).willReturn(false);

        java.util.concurrent.atomic.AtomicLong orderIdSeq = new java.util.concurrent.atomic.AtomicLong(500L);
        given(orderRepository.save(any(OrderEntity.class))).willAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "orderId", orderIdSeq.getAndIncrement());
            return o;
        });
        given(customerSubscriptionRepository.existsByCustomerIdAndStatus(TEST_CUSTOMER_ID, "ACTIVE")).willReturn(true);

        seeder.run(applicationArguments);

        verify(orderRepository, times(24)).save(orderCaptor.capture());
        assertThat(orderCaptor.getAllValues())
                .allSatisfy(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED));

        ArgumentCaptor<Instant> tsCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository, times(24)).updateTimestampsForSeed(any(Long.class), tsCaptor.capture());
        long distinctMonths = tsCaptor.getAllValues().stream()
                .map(ts -> java.time.YearMonth.from(ts.atZone(java.time.ZoneOffset.UTC)))
                .distinct()
                .count();
        assertThat(distinctMonths).isEqualTo(4);

        verify(orderItemRepository, org.mockito.Mockito.atLeast(40)).save(orderItemCaptor.capture());
        assertThat(orderItemCaptor.getAllValues())
                .extracting(OrderItemEntity::getProductId)
                .contains(101L, 102L, 201L, 202L, 203L, 204L, 205L, 301L, 401L);
    }

    private List<ProductSnapshot> defaultSeedProducts() {
        return List.of(
                new ProductSnapshot(101L, "Lumintra Matrix 64x64 Pro", ProductType.LED, "thumb1",
                        new BigDecimal("250000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(202L, "Cyberpunk Cat", ProductType.CHARACTER, "thumb2",
                        new BigDecimal("50000.00"), ProductStatus.PUBLISHED),
                new ProductSnapshot(303L, "Lumintra All-Access VIP Pass", ProductType.SUBSCRIPTION, "thumb3",
                        new BigDecimal("99000.00"), ProductStatus.PUBLISHED)
        );
    }
}
