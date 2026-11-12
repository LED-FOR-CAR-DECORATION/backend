package com.lumintra.backend.commerce.infrastructure.bootstrap;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
public class CommerceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommerceDataSeeder.class);

    private static final BigDecimal DEFAULT_PHYSICAL_PRICE = new BigDecimal("250000.00");
    private static final BigDecimal DEFAULT_DIGITAL_PRICE = new BigDecimal("50000.00");
    private static final BigDecimal DEFAULT_SUBSCRIPTION_PRICE = new BigDecimal("99000.00");
    private static final String LEGACY_SEED_PREFIX = "LMT-SEED-TXN-";
    private static final String LEGACY_SEED_MARKER = "LMT-SEED-TXN-001";
    private static final String SEED_ORDER_MARKER_SUFFIX = "SEED0001";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final OwnedCharacterRepository ownedCharacterRepository;
    private final CustomerLookupService customerLookupService;
    private final ProductLookupService productLookupService;

    @Value("${app.seed.test-customer-email:customer123@gmail.com}")
    private String customerEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking database for base Commerce seed data...");

        CustomerProfileSnapshot customer = resolveCustomer();
        if (customer == null) {
            log.warn("No customer profile found in database (email={}). Skipping Commerce seed data.", customerEmail);
            return;
        }

        Long customerId = customer.customerId();
        log.info("Resolved customer for Commerce seeding [customerId={}] [email={}]", customerId, customer.email());

        ProductSeedContext products = resolveProducts();

        seedShoppingCart(customerId, products);
        seedOrders(customerId, customer, products);
        seedSubscriptionAndOwnership(customerId, products);

        log.info("Commerce seed data verification complete.");
    }

    private CustomerProfileSnapshot resolveCustomer() {
        return customerLookupService.findProfileByEmail(customerEmail)
                .or(customerLookupService::findFirstProfile)
                .orElse(null);
    }

    private ProductSeedContext resolveProducts() {
        List<ProductSnapshot> allProducts = productLookupService.findAllSnapshots();

        ProductSnapshot physical = allProducts.stream()
                .filter(p -> p.productType() == ProductType.LED && p.status() == ProductStatus.PUBLISHED)
                .findFirst()
                .or(() -> allProducts.stream().filter(p -> p.productType() == ProductType.LED).findFirst())
                .orElse(null);

        ProductSnapshot digital = allProducts.stream()
                .filter(p -> p.productType() == ProductType.CHARACTER && p.status() == ProductStatus.PUBLISHED)
                .findFirst()
                .or(() -> allProducts.stream().filter(p -> p.productType() == ProductType.CHARACTER).findFirst())
                .orElse(null);

        ProductSnapshot subscription = allProducts.stream()
                .filter(p -> p.productType() == ProductType.SUBSCRIPTION && p.status() == ProductStatus.PUBLISHED)
                .findFirst()
                .or(() -> allProducts.stream().filter(p -> p.productType() == ProductType.SUBSCRIPTION).findFirst())
                .orElse(null);

        Long physicalId = physical != null ? physical.productId() : 1L;
        BigDecimal physicalPrice = physical != null && physical.basePrice() != null
                ? physical.basePrice() : DEFAULT_PHYSICAL_PRICE;

        Long digitalId = digital != null ? digital.productId() : 2L;
        BigDecimal digitalPrice = digital != null && digital.basePrice() != null
                ? digital.basePrice() : DEFAULT_DIGITAL_PRICE;

        Long subscriptionId = subscription != null ? subscription.productId() : 3L;
        BigDecimal subscriptionPrice = subscription != null && subscription.basePrice() != null
                ? subscription.basePrice() : DEFAULT_SUBSCRIPTION_PRICE;

        return new ProductSeedContext(
                physicalId, physicalPrice,
                digitalId, digitalPrice,
                subscriptionId, subscriptionPrice
        );
    }

    private void seedShoppingCart(Long customerId, ProductSeedContext products) {
        CartEntity cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(CartEntity.create(customerId)));

        List<CartItemEntity> existingItems =
                cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(cart.getCartId());

        if (existingItems.isEmpty()) {
            cartItemRepository.save(CartItemEntity.create(
                    cart, products.physicalId(), 1, products.physicalPrice()));
            cartItemRepository.save(CartItemEntity.create(
                    cart, products.digitalId(), 1, products.digitalPrice()));
            cartItemRepository.save(CartItemEntity.create(
                    cart, products.subscriptionId(), 1, products.subscriptionPrice()));

            log.info("Seeded shopping cart with 3 items [customerId={}] [cartId={}]", customerId, cart.getCartId());
        } else {
            log.info("Shopping cart already has items [customerId={}] [cartId={}] [count={}]. Skipping cart items seeding.",
                    customerId, cart.getCartId(), existingItems.size());
        }
    }

    private void seedOrders(Long customerId, CustomerProfileSnapshot customer, ProductSeedContext products) {
        if (paymentTransactionRepository.existsByTransactionCode(LEGACY_SEED_MARKER)) {
            migrateLegacySeedTransactionCodes();
            return;
        }
        if (paymentTransactionRepository.existsByTransactionCodeEndingWith(SEED_ORDER_MARKER_SUFFIX)) {
            log.info("Commerce orders already seeded [marker=*{}]. Skipping order generation.", SEED_ORDER_MARKER_SUFFIX);
            return;
        }

        Instant now = Instant.now();
        String recipientName = customer.fullName() != null && !customer.fullName().isBlank()
                ? customer.fullName() : "Test Customer";
        String recipientPhone = customer.phone() != null && !customer.phone().isBlank()
                ? customer.phone() : "0901234567";

        // 1. PENDING_PAYMENT (Physical, delivery PENDING, payment PENDING) -> tests customer QR payment countdown & staff cancel
        createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.PENDING_PAYMENT,
                products.physicalPrice(),
                now.plus(7, ChronoUnit.DAYS),
                products.physicalId(),
                1,
                products.physicalPrice(),
                new ShippingSeed(
                        recipientName,
                        recipientPhone,
                        "123 Le Loi, Ben Nghe, District 1, Ho Chi Minh City",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        DeliveryStatus.PENDING,
                        null, null, null, null, null
                ),
                new PaymentSeed(
                        "SEED0001",
                        PaymentStatus.PENDING,
                        null
                )
        );

        // 2. PROCESSING (Physical, delivery PENDING, payment SUCCESS) -> default staff queue, ready for AhaMove dispatch
        BigDecimal procTotal = products.physicalPrice().multiply(BigDecimal.valueOf(2));
        createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.PROCESSING,
                procTotal,
                now.minus(1, ChronoUnit.HOURS),
                products.physicalId(),
                2,
                products.physicalPrice(),
                new ShippingSeed(
                        "Tran Thi B",
                        "0912345678",
                        "456 Nguyen Thi Minh Khai, Ward 5, District 3, Ho Chi Minh City",
                        new BigDecimal("10.7681230"),
                        new BigDecimal("106.6894560"),
                        DeliveryStatus.PENDING,
                        null, null, null, null, null
                ),
                new PaymentSeed(
                        "SEED0002",
                        PaymentStatus.SUCCESS,
                        now.minus(2, ChronoUnit.HOURS)
                )
        );

        // 3. PARTIALLY_FULFILLED (Mixed, delivery PENDING, payment SUCCESS) -> default staff queue, mixed order
        BigDecimal mixedSubtotal = products.physicalPrice().add(products.digitalPrice());
        OrderEntity order3 = createOrderWithMultipleItems(
                customerId,
                OrderType.MIXED,
                OrderStatus.PARTIALLY_FULFILLED,
                mixedSubtotal,
                now.minus(1, ChronoUnit.DAYS),
                List.of(
                        new ItemSeed(products.physicalId(), 1, products.physicalPrice()),
                        new ItemSeed(products.digitalId(), 1, products.digitalPrice())
                ),
                new ShippingSeed(
                        "Le Van C",
                        "0987654321",
                        "Lot E2a-7, D1 Street, Saigon Hi-Tech Park, Thu Duc City, Ho Chi Minh City",
                        new BigDecimal("10.8411000"),
                        new BigDecimal("106.8099000"),
                        DeliveryStatus.PENDING,
                        null, null, null, null, null
                ),
                new PaymentSeed(
                        "SEED0003",
                        PaymentStatus.SUCCESS,
                        now.minus(1, ChronoUnit.DAYS)
                )
        );

        // 4. SHIPPING (Physical, delivery SHIPPING, AhaMove tracking, payment SUCCESS) -> in transit, ready for webhook test
        createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.SHIPPING,
                products.physicalPrice(),
                now.minus(2, ChronoUnit.DAYS),
                products.physicalId(),
                1,
                products.physicalPrice(),
                new ShippingSeed(
                        "Pham Van D",
                        "0933333333",
                        "789 Vo Van Kiet, Ward 1, District 5, Ho Chi Minh City",
                        new BigDecimal("10.7512340"),
                        new BigDecimal("106.6712340"),
                        DeliveryStatus.SHIPPING,
                        "AHA-LMT-TRACK-8888",
                        "https://ahamove.com/tracking/AHA-LMT-TRACK-8888",
                        new BigDecimal("25000.00"),
                        now.plus(4, ChronoUnit.HOURS),
                        null
                ),
                new PaymentSeed(
                        "SEED0004",
                        PaymentStatus.SUCCESS,
                        now.minus(2, ChronoUnit.DAYS)
                )
        );

        // 5. DELIVERED (Physical, delivery DELIVERED, payment SUCCESS) -> customer confirm-delivery (COM-15) test
        createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.DELIVERED,
                products.physicalPrice(),
                now.minus(3, ChronoUnit.DAYS),
                products.physicalId(),
                1,
                products.physicalPrice(),
                new ShippingSeed(
                        "Hoang Thi E",
                        "0944444444",
                        "101 Nam Ky Khoi Nghia, Ben Nghe, District 1, Ho Chi Minh City",
                        new BigDecimal("10.7745000"),
                        new BigDecimal("106.6998000"),
                        DeliveryStatus.DELIVERED,
                        "AHA-LMT-TRACK-9999",
                        "https://ahamove.com/tracking/AHA-LMT-TRACK-9999",
                        new BigDecimal("30000.00"),
                        null,
                        now.minus(1, ChronoUnit.HOURS)
                ),
                new PaymentSeed(
                        "SEED0005",
                        PaymentStatus.SUCCESS,
                        now.minus(3, ChronoUnit.DAYS)
                )
        );

        // 6. DELIVERY_FAILED (Physical, delivery FAILED, payment SUCCESS) -> staff re-transition to PROCESSING (COM-12)
        createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.DELIVERY_FAILED,
                products.physicalPrice(),
                now.minus(4, ChronoUnit.DAYS),
                products.physicalId(),
                1,
                products.physicalPrice(),
                new ShippingSeed(
                        "Vu Van F",
                        "0955555555",
                        "202 Pasteur, Ward 6, District 3, Ho Chi Minh City",
                        new BigDecimal("10.7821000"),
                        new BigDecimal("106.6932000"),
                        DeliveryStatus.FAILED,
                        "AHA-LMT-TRACK-FAIL-01",
                        "https://ahamove.com/tracking/AHA-LMT-TRACK-FAIL-01",
                        new BigDecimal("25000.00"),
                        null,
                        null
                ),
                new PaymentSeed(
                        "SEED0006",
                        PaymentStatus.SUCCESS,
                        now.minus(4, ChronoUnit.DAYS)
                )
        );

        // 7. COMPLETED (Physical, delivery DELIVERED, payment SUCCESS) -> completed order in history
        createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.COMPLETED,
                products.physicalPrice(),
                now.minus(5, ChronoUnit.DAYS),
                products.physicalId(),
                1,
                products.physicalPrice(),
                new ShippingSeed(
                        "Do Van G",
                        "0966666666",
                        "303 Hai Ba Trung, Ward 8, District 3, Ho Chi Minh City",
                        new BigDecimal("10.7891000"),
                        new BigDecimal("106.6882000"),
                        DeliveryStatus.DELIVERED,
                        "AHA-LMT-TRACK-COMP-01",
                        null,
                        null,
                        null,
                        now.minus(2, ChronoUnit.DAYS)
                ),
                new PaymentSeed(
                        "SEED0007",
                        PaymentStatus.SUCCESS,
                        now.minus(5, ChronoUnit.DAYS)
                )
        );

        // 8. CANCELED (Physical, delivery PENDING, payment EXPIRED) -> canceled order filter test
        createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.CANCELED,
                products.physicalPrice(),
                now.minus(6, ChronoUnit.DAYS),
                products.physicalId(),
                1,
                products.physicalPrice(),
                new ShippingSeed(
                        "Ngo Thi H",
                        "0977777777",
                        "404 Tran Hung Dao, District 5, Ho Chi Minh City",
                        new BigDecimal("10.7533000"),
                        new BigDecimal("106.6744000"),
                        DeliveryStatus.PENDING,
                        null, null, null, null, null
                ),
                new PaymentSeed(
                        "SEED0008",
                        PaymentStatus.FAILED,
                        null
                )
        );

        // 9. DIGITAL (COMPLETED, pure digital character order without delivery) -> customer digital history
        createOrder(
                customerId,
                OrderType.DIGITAL,
                OrderStatus.COMPLETED,
                products.digitalPrice(),
                now.minus(7, ChronoUnit.DAYS),
                products.digitalId(),
                1,
                products.digitalPrice(),
                null, // No delivery record for digital goods
                new PaymentSeed(
                        "SEED0009",
                        PaymentStatus.SUCCESS,
                        now.minus(7, ChronoUnit.DAYS)
                )
        );

        // 10. EXPIRED_PENDING_PAYMENT (Physical, delivery PENDING, payment PENDING, expired 2 hours ago) -> tests expired order UI & no QR
        createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.PENDING_PAYMENT,
                products.physicalPrice(),
                now.minus(2, ChronoUnit.HOURS),
                products.physicalId(),
                1,
                products.physicalPrice(),
                new ShippingSeed(
                        "Tran Thi Mai",
                        "0909999888",
                        "555 Cach Mang Thang 8, Ward 11, District 3, Ho Chi Minh City",
                        new BigDecimal("10.7834500"),
                        new BigDecimal("106.6712300"),
                        DeliveryStatus.PENDING,
                        null, null, null, null, null
                ),
                new PaymentSeed(
                        "SEED0010",
                        PaymentStatus.PENDING,
                        null
                )
        );

        log.info("Seeded 10 representative orders across all lifecycle states [customerId={}]", customerId);
    }

    private void migrateLegacySeedTransactionCodes() {
        List<PaymentTransactionEntity> legacyTransactions =
                paymentTransactionRepository.findByTransactionCodeStartingWith(LEGACY_SEED_PREFIX);
        if (legacyTransactions.isEmpty()) {
            return;
        }
        for (PaymentTransactionEntity transaction : legacyTransactions) {
            String legacyCode = transaction.getTransactionCode();
            if (legacyCode != null && legacyCode.startsWith(LEGACY_SEED_PREFIX)) {
                String indexPart = legacyCode.substring(LEGACY_SEED_PREFIX.length());
                String normalizedSuffix = "SEED" + (indexPart.length() == 3 ? "0" + indexPart : indexPart);
                transaction.setTransactionCode(buildSeedPaymentCode(transaction.getOrderId(), normalizedSuffix));
            }
        }
        paymentTransactionRepository.saveAll(legacyTransactions);
        log.info("Migrated {} legacy Commerce seed payment transaction codes to LMTORD format.",
                legacyTransactions.size());
    }

    private String buildSeedPaymentCode(Long orderId, String suffix) {
        return "LMTORD" + orderId + suffix;
    }

    private void seedSubscriptionAndOwnership(Long customerId, ProductSeedContext products) {
        if (!customerSubscriptionRepository.existsByCustomerIdAndStatus(customerId, "ACTIVE")) {
            CustomerSubscriptionEntity subscription = CustomerSubscriptionEntity.createActive(
                    customerId, products.subscriptionId(), null);
            customerSubscriptionRepository.save(subscription);
            log.info("Seeded active customer subscription [customerId={}] [productId={}]",
                    customerId, products.subscriptionId());
        }

        // Seed an owned character (version 99L) so customer character library is populated without conflicting with cart checkout
        if (!ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, 99L)) {
            OwnedCharacterEntity owned = OwnedCharacterEntity.create(customerId, 99L, null);
            ownedCharacterRepository.save(owned);
            log.info("Seeded active owned character [customerId={}] [versionId=99]", customerId);
        }
    }

    private OrderEntity createOrder(
            Long customerId,
            OrderType orderType,
            OrderStatus status,
            BigDecimal totalAmount,
            Instant expiresAt,
            Long productId,
            int quantity,
            BigDecimal unitPrice,
            ShippingSeed shipping,
            PaymentSeed payment) {
        return createOrderWithMultipleItems(
                customerId,
                orderType,
                status,
                totalAmount,
                expiresAt,
                List.of(new ItemSeed(productId, quantity, unitPrice)),
                shipping,
                payment
        );
    }

    private OrderEntity createOrderWithMultipleItems(
            Long customerId,
            OrderType orderType,
            OrderStatus status,
            BigDecimal totalAmount,
            Instant expiresAt,
            List<ItemSeed> items,
            ShippingSeed shipping,
            PaymentSeed payment) {

        OrderEntity order = OrderEntity.createPendingPayment(
                customerId,
                null,
                totalAmount,
                BigDecimal.ZERO,
                totalAmount,
                orderType,
                expiresAt
        );
        order.setStatus(status);
        OrderEntity savedOrder = orderRepository.save(order);

        for (ItemSeed item : items) {
            orderItemRepository.save(OrderItemEntity.create(
                    savedOrder, item.productId(), item.quantity(), item.unitPrice()));
        }

        if (shipping != null) {
            OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                    savedOrder,
                    new ResolvedShippingAddress(
                            null,
                            shipping.recipientName(),
                            shipping.recipientPhone(),
                            shipping.fullAddress(),
                            shipping.latitude(),
                            shipping.longitude()
                    )
            );
            delivery.setDeliveryStatus(shipping.deliveryStatus());
            if (shipping.trackingNumber() != null) {
                delivery.setTrackingNumber(shipping.trackingNumber());
            }
            if (shipping.trackingUrl() != null) {
                delivery.setTrackingUrl(shipping.trackingUrl());
            }
            if (shipping.carrierFee() != null) {
                delivery.setCarrierFee(shipping.carrierFee());
            }
            if (shipping.estimatedDeliveryAt() != null) {
                delivery.setEstimatedDeliveryAt(shipping.estimatedDeliveryAt());
            }
            if (shipping.deliveredAt() != null) {
                delivery.setDeliveredAt(shipping.deliveredAt());
            }
            orderDeliveryRepository.save(delivery);
        }

        if (payment != null) {
            PaymentTransactionEntity transaction = PaymentTransactionEntity.createPending(
                    savedOrder.getOrderId(),
                    PaymentGateway.SEPAY,
                    buildSeedPaymentCode(savedOrder.getOrderId(), payment.codeSuffix()),
                    totalAmount
            );
            transaction.setStatus(payment.status());
            if (payment.paidAt() != null) {
                transaction.setPaidAt(payment.paidAt());
            }
            paymentTransactionRepository.save(transaction);
        }

        return savedOrder;
    }

    private record ProductSeedContext(
            Long physicalId, BigDecimal physicalPrice,
            Long digitalId, BigDecimal digitalPrice,
            Long subscriptionId, BigDecimal subscriptionPrice
    ) {}

    private record ItemSeed(Long productId, int quantity, BigDecimal unitPrice) {}

    private record ShippingSeed(
            String recipientName,
            String recipientPhone,
            String fullAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            DeliveryStatus deliveryStatus,
            String trackingNumber,
            String trackingUrl,
            BigDecimal carrierFee,
            Instant estimatedDeliveryAt,
            Instant deliveredAt
    ) {}

    private record PaymentSeed(
            String codeSuffix,
            PaymentStatus status,
            Instant paidAt
    ) {}
}
