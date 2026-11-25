package com.lumintra.backend.commerce.infrastructure.bootstrap;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Order(3)
@RequiredArgsConstructor
public class CommerceDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommerceDataSeeder.class);

    private static final BigDecimal WEBHOOK_TEST_ORDER_AMOUNT = new BigDecimal("10000.00");
    private static final String LEGACY_SEED_PREFIX = "LMT-SEED-TXN-";
    private static final String LEGACY_SEED_MARKER = "LMT-SEED-TXN-001";
    private static final String SEED_ORDER_MARKER_SUFFIX = "SEED0001";
    private static final String WEBHOOK_TEST_SEED_MARKER_SUFFIX = "SEED0011";
    private static final String WARRANTY_ORDERS_SEED_MARKER_SUFFIX = "SEED0012";
    private static final String DASHBOARD_SALES_SEED_MARKER_SUFFIX = "SALES001";

    private static final String SEED_CUSTOMER_2_EMAIL = "minhanh.nguyen@gmail.com";
    private static final String SEED_CUSTOMER_3_EMAIL = "quocbao.tran@gmail.com";
    private static final String SEED_CUSTOMER_4_EMAIL = "thaovy.le@gmail.com";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final OwnedCharacterRepository ownedCharacterRepository;
    private final CustomerLookupService customerLookupService;
    private final ProductLookupService productLookupService;
    private final CharacterLookupService characterLookupService;

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

        ProductSeedContext products = resolveProducts();
        if (products == null) {
            log.warn("Required Store products (LED, CHARACTER, SUBSCRIPTION) not found in database. Skipping Commerce seed data.");
            return;
        }

        Long customerId = customer.customerId();
        log.info("Resolved customer for Commerce seeding [customerId={}] [email={}]", customerId, customer.email());

        seedOrders(customerId, customer, products);
        seedWebhookTestOrder(customerId, customer, products);
        seedWarrantyAndAdditionalOrders(customerId, customer, products);
        seedDashboardSalesHistory(customer, products);
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

        ProductSnapshot physical = findPreferredProduct(allProducts, ProductType.LED);
        ProductSnapshot secondaryPhysical = findSecondaryLedProduct(allProducts, physical);
        ProductSnapshot digital = findPreferredProduct(allProducts, ProductType.CHARACTER);
        ProductSnapshot subscription = findPreferredProduct(allProducts, ProductType.SUBSCRIPTION);

        if (!isValidSeedProduct(physical) || !isValidSeedProduct(digital) || !isValidSeedProduct(subscription)) {
            return null;
        }

        Long ownedCharacterVersionId = resolveOwnedCharacterVersionId(allProducts, digital.productId());

        List<ProductSnapshot> ledProducts = filterPublishedByType(allProducts, ProductType.LED, physical);
        List<ProductSnapshot> characterProducts = filterPublishedByType(allProducts, ProductType.CHARACTER, digital);
        List<ProductSnapshot> bundleProducts = filterPublishedByType(allProducts, ProductType.BUNDLE, null);
        List<ProductSnapshot> subscriptionProducts = filterPublishedByType(allProducts, ProductType.SUBSCRIPTION, subscription);

        return new ProductSeedContext(
                physical.productId(), physical.basePrice(),
                secondaryPhysical.productId(), secondaryPhysical.basePrice(),
                digital.productId(), digital.basePrice(),
                subscription.productId(), subscription.basePrice(),
                ownedCharacterVersionId,
                ledProducts,
                characterProducts,
                bundleProducts,
                subscriptionProducts
        );
    }

    private List<ProductSnapshot> filterPublishedByType(
            List<ProductSnapshot> allProducts, ProductType type, ProductSnapshot fallback) {
        List<ProductSnapshot> matches = allProducts.stream()
                .filter(p -> p.productType() == type
                        && p.status() == ProductStatus.PUBLISHED
                        && isValidSeedProduct(p))
                .toList();
        if (!matches.isEmpty()) {
            return matches;
        }
        return isValidSeedProduct(fallback) ? List.of(fallback) : List.of();
    }

    private ProductSnapshot findSecondaryLedProduct(List<ProductSnapshot> allProducts, ProductSnapshot primaryPhysical) {
        if (primaryPhysical == null) {
            return null;
        }
        return allProducts.stream()
                .filter(p -> p.productType() == ProductType.LED
                        && p.status() == ProductStatus.PUBLISHED
                        && p.productId() != null
                        && !p.productId().equals(primaryPhysical.productId()))
                .findFirst()
                .orElse(primaryPhysical);
    }

    private ProductSnapshot findPreferredProduct(List<ProductSnapshot> allProducts, ProductType type) {
        return allProducts.stream()
                .filter(p -> p.productType() == type && p.status() == ProductStatus.PUBLISHED)
                .findFirst()
                .or(() -> allProducts.stream().filter(p -> p.productType() == type).findFirst())
                .orElse(null);
    }

    private boolean isValidSeedProduct(ProductSnapshot product) {
        return product != null && product.productId() != null && product.basePrice() != null;
    }

    private Long resolveOwnedCharacterVersionId(List<ProductSnapshot> allProducts, Long cartDigitalProductId) {
        return allProducts.stream()
                .filter(p -> p.productType() == ProductType.CHARACTER
                        && p.productId() != null
                        && !p.productId().equals(cartDigitalProductId)
                        && p.status() == ProductStatus.ARCHIVED)
                .findFirst()
                .or(() -> allProducts.stream()
                        .filter(p -> p.productType() == ProductType.CHARACTER
                                && p.productId() != null
                                && !p.productId().equals(cartDigitalProductId))
                        .reduce((first, second) -> second))
                .flatMap(candidate -> characterLookupService.findVersionIdByProductId(candidate.productId()))
                .orElse(null);
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
        ProductSnapshot order3Char = pickDistinctCharacter(products, 3, products.digitalId());
        BigDecimal mixedSubtotal = products.physicalPrice().add(order3Char.basePrice());
        createOrderWithMultipleItems(
                customerId,
                OrderType.MIXED,
                OrderStatus.PARTIALLY_FULFILLED,
                mixedSubtotal,
                now.minus(1, ChronoUnit.DAYS),
                List.of(
                        new ItemSeed(products.physicalId(), 1, products.physicalPrice()),
                        new ItemSeed(order3Char.productId(), 1, order3Char.basePrice(), true)
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
        ProductSnapshot order9Char = pickDistinctCharacter(products, 5, products.digitalId());
        createOrderWithMultipleItems(
                customerId,
                OrderType.DIGITAL,
                OrderStatus.COMPLETED,
                order9Char.basePrice(),
                now.minus(7, ChronoUnit.DAYS),
                List.of(new ItemSeed(order9Char.productId(), 1, order9Char.basePrice(), true)),
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

    private void seedWebhookTestOrder(Long customerId, CustomerProfileSnapshot customer, ProductSeedContext products) {
        if (paymentTransactionRepository.existsByTransactionCodeEndingWith(WEBHOOK_TEST_SEED_MARKER_SUFFIX)) {
            log.info("Webhook test order already seeded [marker=*{}]. Skipping.", WEBHOOK_TEST_SEED_MARKER_SUFFIX);
            return;
        }

        Instant now = Instant.now();
        String recipientName = customer.fullName() != null && !customer.fullName().isBlank()
                ? customer.fullName() : "Test Customer";
        String recipientPhone = customer.phone() != null && !customer.phone().isBlank()
                ? customer.phone() : "0901234567";

        // 11. PENDING_PAYMENT (10,000 VND) -> lightweight real-transfer SePay webhook testing
        OrderEntity webhookTestOrder = createOrder(
                customerId,
                OrderType.PHYSICAL,
                OrderStatus.PENDING_PAYMENT,
                WEBHOOK_TEST_ORDER_AMOUNT,
                now.plus(7, ChronoUnit.DAYS),
                products.physicalId(),
                1,
                WEBHOOK_TEST_ORDER_AMOUNT,
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
                        WEBHOOK_TEST_SEED_MARKER_SUFFIX,
                        PaymentStatus.PENDING,
                        null
                )
        );

        log.info("Seeded 10,000 VND webhook test order [orderId={}] [paymentCode={}]",
                webhookTestOrder.getOrderId(),
                buildSeedPaymentCode(webhookTestOrder.getOrderId(), WEBHOOK_TEST_SEED_MARKER_SUFFIX));
    }

    private void seedWarrantyAndAdditionalOrders(
            Long primaryCustomerId,
            CustomerProfileSnapshot primaryCustomer,
            ProductSeedContext products) {
        if (paymentTransactionRepository.existsByTransactionCodeEndingWith(WARRANTY_ORDERS_SEED_MARKER_SUFFIX)) {
            log.info("Warranty & additional customer orders already seeded [marker=*{}]. Skipping.",
                    WARRANTY_ORDERS_SEED_MARKER_SUFFIX);
            return;
        }

        Instant now = Instant.now();
        String primaryName = primaryCustomer.fullName() != null && !primaryCustomer.fullName().isBlank()
                ? primaryCustomer.fullName() : "Test Customer";
        String primaryPhone = primaryCustomer.phone() != null && !primaryCustomer.phone().isBlank()
                ? primaryCustomer.phone() : "0901234567";

        int seededOrders = 0;

        // 12. COMPLETED MIXED (Primary customer: active clean LED + character) -> for clean warranty claim submission
        ProductSnapshot order12Char = pickDistinctCharacter(products, 6, products.digitalId());
        BigDecimal mixedProTotal = products.physicalPrice().add(order12Char.basePrice());
        createOrderWithMultipleItems(
                primaryCustomerId,
                OrderType.MIXED,
                OrderStatus.COMPLETED,
                mixedProTotal,
                now.minus(12, ChronoUnit.DAYS),
                List.of(
                        new ItemSeed(products.physicalId(), 1, products.physicalPrice()),
                        new ItemSeed(order12Char.productId(), 1, order12Char.basePrice(), true)
                ),
                new ShippingSeed(
                        primaryName,
                        primaryPhone,
                        "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        DeliveryStatus.DELIVERED,
                        "AHA-LMT-WARR-0012",
                        "https://ahamove.com/tracking/AHA-LMT-WARR-0012",
                        new BigDecimal("25000.00"),
                        null,
                        now.minus(10, ChronoUnit.DAYS)
                ),
                new PaymentSeed("SEED0012", PaymentStatus.SUCCESS, now.minus(12, ChronoUnit.DAYS))
        );
        seededOrders++;

        // 13. COMPLETED PHYSICAL (Primary customer: Compact LED delivered 200 days ago) -> for expired warranty test
        createOrder(
                primaryCustomerId,
                OrderType.PHYSICAL,
                OrderStatus.COMPLETED,
                products.secondaryPhysicalPrice(),
                now.minus(202, ChronoUnit.DAYS),
                products.secondaryPhysicalId(),
                1,
                products.secondaryPhysicalPrice(),
                new ShippingSeed(
                        primaryName,
                        primaryPhone,
                        "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        DeliveryStatus.DELIVERED,
                        "AHA-LMT-WARR-0013",
                        "https://ahamove.com/tracking/AHA-LMT-WARR-0013",
                        new BigDecimal("25000.00"),
                        null,
                        now.minus(200, ChronoUnit.DAYS)
                ),
                new PaymentSeed("SEED0013", PaymentStatus.SUCCESS, now.minus(202, ChronoUnit.DAYS))
        );
        seededOrders++;

        CustomerProfileSnapshot customer2 = customerLookupService.findProfileByEmail(SEED_CUSTOMER_2_EMAIL).orElse(null);
        if (customer2 != null) {
            String c2Phone = customer2.phone() != null ? customer2.phone() : "0902345678";
            // 14. Customer 2 COMPLETED PHYSICAL -> linked to PENDING warranty claim
            createOrder(
                    customer2.customerId(),
                    OrderType.PHYSICAL,
                    OrderStatus.COMPLETED,
                    products.physicalPrice(),
                    now.minus(17, ChronoUnit.DAYS),
                    products.physicalId(),
                    1,
                    products.physicalPrice(),
                    new ShippingSeed(
                            customer2.fullName(),
                            c2Phone,
                            "88 Đồng Khởi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                            new BigDecimal("10.7751000"),
                            new BigDecimal("106.7031000"),
                            DeliveryStatus.DELIVERED,
                            "AHA-LMT-WARR-0014",
                            "https://ahamove.com/tracking/AHA-LMT-WARR-0014",
                            new BigDecimal("22000.00"),
                            null,
                            now.minus(15, ChronoUnit.DAYS)
                    ),
                    new PaymentSeed("SEED0014", PaymentStatus.SUCCESS, now.minus(17, ChronoUnit.DAYS))
            );
            seededOrders++;

            // 15. Customer 2 COMPLETED MIXED -> linked to APPROVED warranty claim
            ProductSnapshot c2OrderChar = pickDistinctCharacter(products, 2, products.digitalId());
            BigDecimal c2MixedTotal = products.secondaryPhysicalPrice().add(c2OrderChar.basePrice());
            createOrderWithMultipleItems(
                    customer2.customerId(),
                    OrderType.MIXED,
                    OrderStatus.COMPLETED,
                    c2MixedTotal,
                    now.minus(27, ChronoUnit.DAYS),
                    List.of(
                            new ItemSeed(products.secondaryPhysicalId(), 1, products.secondaryPhysicalPrice()),
                            new ItemSeed(c2OrderChar.productId(), 1, c2OrderChar.basePrice(), true)
                    ),
                    new ShippingSeed(
                            customer2.fullName(),
                            c2Phone,
                            "88 Đồng Khởi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                            new BigDecimal("10.7751000"),
                            new BigDecimal("106.7031000"),
                            DeliveryStatus.DELIVERED,
                            "AHA-LMT-WARR-0015",
                            "https://ahamove.com/tracking/AHA-LMT-WARR-0015",
                            new BigDecimal("22000.00"),
                            null,
                            now.minus(25, ChronoUnit.DAYS)
                    ),
                    new PaymentSeed("SEED0015", PaymentStatus.SUCCESS, now.minus(27, ChronoUnit.DAYS))
            );
            seededOrders++;
        }

        CustomerProfileSnapshot customer3 = customerLookupService.findProfileByEmail(SEED_CUSTOMER_3_EMAIL).orElse(null);
        if (customer3 != null) {
            String c3Phone = customer3.phone() != null ? customer3.phone() : "0903456789";
            // 16. Customer 3 COMPLETED PHYSICAL (qty 2) -> linked to COMPLETED warranty claim
            BigDecimal c3Total = products.physicalPrice().multiply(BigDecimal.valueOf(2));
            createOrder(
                    customer3.customerId(),
                    OrderType.PHYSICAL,
                    OrderStatus.COMPLETED,
                    c3Total,
                    now.minus(42, ChronoUnit.DAYS),
                    products.physicalId(),
                    2,
                    products.physicalPrice(),
                    new ShippingSeed(
                            customer3.fullName(),
                            c3Phone,
                            "250 Nguyễn Văn Linh, Phường Tân Phong, Quận 7, TP. Hồ Chí Minh",
                            new BigDecimal("10.7295000"),
                            new BigDecimal("106.7120000"),
                            DeliveryStatus.DELIVERED,
                            "AHA-LMT-WARR-0016",
                            "https://ahamove.com/tracking/AHA-LMT-WARR-0016",
                            new BigDecimal("30000.00"),
                            null,
                            now.minus(40, ChronoUnit.DAYS)
                    ),
                    new PaymentSeed("SEED0016", PaymentStatus.SUCCESS, now.minus(42, ChronoUnit.DAYS))
            );
            seededOrders++;
        }

        CustomerProfileSnapshot customer4 = customerLookupService.findProfileByEmail(SEED_CUSTOMER_4_EMAIL).orElse(null);
        if (customer4 != null) {
            String c4Phone = customer4.phone() != null ? customer4.phone() : "0904567890";
            // 17. Customer 4 COMPLETED PHYSICAL -> linked to REJECTED warranty claim
            createOrder(
                    customer4.customerId(),
                    OrderType.PHYSICAL,
                    OrderStatus.COMPLETED,
                    products.secondaryPhysicalPrice(),
                    now.minus(14, ChronoUnit.DAYS),
                    products.secondaryPhysicalId(),
                    1,
                    products.secondaryPhysicalPrice(),
                    new ShippingSeed(
                            customer4.fullName(),
                            c4Phone,
                            "15 Võ Văn Ngân, Phường Linh Chiểu, TP. Thủ Đức, TP. Hồ Chí Minh",
                            new BigDecimal("10.8505000"),
                            new BigDecimal("106.7652000"),
                            DeliveryStatus.DELIVERED,
                            "AHA-LMT-WARR-0017",
                            "https://ahamove.com/tracking/AHA-LMT-WARR-0017",
                            new BigDecimal("28000.00"),
                            null,
                            now.minus(12, ChronoUnit.DAYS)
                    ),
                    new PaymentSeed("SEED0017", PaymentStatus.SUCCESS, now.minus(14, ChronoUnit.DAYS))
            );
            seededOrders++;

            // 18. Customer 4 COMPLETED PHYSICAL -> linked to PENDING warranty claim
            createOrder(
                    customer4.customerId(),
                    OrderType.PHYSICAL,
                    OrderStatus.COMPLETED,
                    products.physicalPrice(),
                    now.minus(9, ChronoUnit.DAYS),
                    products.physicalId(),
                    1,
                    products.physicalPrice(),
                    new ShippingSeed(
                            customer4.fullName(),
                            c4Phone,
                            "15 Võ Văn Ngân, Phường Linh Chiểu, TP. Thủ Đức, TP. Hồ Chí Minh",
                            new BigDecimal("10.8505000"),
                            new BigDecimal("106.7652000"),
                            DeliveryStatus.DELIVERED,
                            "AHA-LMT-WARR-0018",
                            "https://ahamove.com/tracking/AHA-LMT-WARR-0018",
                            new BigDecimal("28000.00"),
                            null,
                            now.minus(7, ChronoUnit.DAYS)
                    ),
                    new PaymentSeed("SEED0018", PaymentStatus.SUCCESS, now.minus(9, ChronoUnit.DAYS))
            );
            seededOrders++;
        }

        log.info("Seeded {} additional warranty & multi-customer orders.", seededOrders);
    }

    private ProductSnapshot pickDistinctCharacter(
            ProductSeedContext products, int preferredIndex, Long fallbackProductId) {
        List<ProductSnapshot> chars = products.characterProducts();
        if (chars != null && chars.size() > preferredIndex) {
            return chars.get(preferredIndex);
        }
        return chars != null && !chars.isEmpty()
                ? chars.get(0)
                : new ProductSnapshot(fallbackProductId, "Character", ProductType.CHARACTER, null, products.digitalPrice(), ProductStatus.PUBLISHED);
    }

    private void seedDashboardSalesHistory(
            CustomerProfileSnapshot primaryCustomer,
            ProductSeedContext products) {
        if (paymentTransactionRepository.existsByTransactionCodeEndingWith(DASHBOARD_SALES_SEED_MARKER_SUFFIX)) {
            log.info("Dashboard multi-month sales history already seeded [marker=*{}]. Skipping.",
                    DASHBOARD_SALES_SEED_MARKER_SUFFIX);
            return;
        }

        List<CustomerProfileSnapshot> customerPool = resolveSalesCustomerPool(primaryCustomer);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        YearMonth currentYm = YearMonth.from(today);
        YearMonth prev1Ym = currentYm.minusMonths(1);
        YearMonth prev2Ym = currentYm.minusMonths(2);
        YearMonth prev3Ym = currentYm.minusMonths(3);

        List<ProductSnapshot> leds = products.ledProducts();
        List<ProductSnapshot> chars = products.characterProducts();
        List<ProductSnapshot> bundles = products.bundleProducts();
        List<ProductSnapshot> subs = products.subscriptionProducts();

        ProductSnapshot led0 = pickAt(leds, 0);
        ProductSnapshot led1 = pickAt(leds, 1);
        ProductSnapshot led2 = pickAt(leds, 2);
        ProductSnapshot bundle0 = bundles.isEmpty() ? null : bundles.get(0);
        ProductSnapshot bundle1 = bundles.size() > 1 ? bundles.get(1) : bundle0;
        ProductSnapshot sub0 = pickAt(subs, 0);
        ProductSnapshot sub1 = subs.size() > 1 ? subs.get(1) : sub0;

        // Track digital/bundle products already bought per customer so no customer ever buys duplicates.
        Map<Long, Set<Long>> usedDigitalByCustomer = new HashMap<>();
        Long primaryId = primaryCustomer.customerId();
        if (chars.size() >= 7) {
            // Reserve characters already seeded in primary customer's Orders 3, 9, 12 + Combo1/Combo2 characters
            // so primary test customer can still demo purchasing Combo 1 and Combo 2 cleanly.
            Set<Long> primaryReserved = usedDigitalByCustomer.computeIfAbsent(primaryId, ignored -> new HashSet<>());
            for (int reservedIdx : new int[]{0, 1, 3, 4, 5, 6, 8, 9}) {
                if (reservedIdx < chars.size() && chars.get(reservedIdx).productId() != null) {
                    primaryReserved.add(chars.get(reservedIdx).productId());
                }
            }
            if (bundle0 != null && bundle0.productId() != null) {
                primaryReserved.add(bundle0.productId());
            }
            if (bundle1 != null && bundle1.productId() != null) {
                primaryReserved.add(bundle1.productId());
            }
            if (sub0 != null && sub0.productId() != null) {
                primaryReserved.add(sub0.productId());
            }
            if (sub1 != null && sub1.productId() != null) {
                primaryReserved.add(sub1.productId());
            }
        }
        if (customerPool.size() > 1 && chars.size() > 2 && chars.get(2).productId() != null) {
            // Customer 2 already purchased chars[2] in Order 15
            usedDigitalByCustomer
                    .computeIfAbsent(customerPool.get(1).customerId(), ignored -> new HashSet<>())
                    .add(chars.get(2).productId());
        }

        int seq = 1;

        // ==================== CURRENT MONTH (8 orders, distributed up to today for MTD) ====================
        // Order 1: Primary LED + 1 unique character (MIXED)
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 0),
                resolveCurrentMonthInstant(today, 0.12, 9), OrderType.MIXED,
                buildSalesItems(pickCustomer(customerPool, 0), usedDigitalByCustomer, led0,
                        item(led0, 2),
                        uniqueDigitalItem(pickCustomer(customerPool, 0), chars, 7, usedDigitalByCustomer)));
        // Order 2: Multiple distinct characters qty=1 (DIGITAL)
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 1),
                resolveCurrentMonthInstant(today, 0.24, 11), OrderType.DIGITAL,
                buildSalesItems(pickCustomer(customerPool, 1), usedDigitalByCustomer, led0,
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 0, usedDigitalByCustomer),
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 3, usedDigitalByCustomer)));
        // Order 3: Bundle + unique character qty=1 (MIXED)
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 2),
                resolveCurrentMonthInstant(today, 0.36, 14), OrderType.MIXED,
                buildSalesItems(pickCustomer(customerPool, 2), usedDigitalByCustomer, led1,
                        uniqueSingleProduct(pickCustomer(customerPool, 2), bundle0, usedDigitalByCustomer, led1),
                        uniqueDigitalItem(pickCustomer(customerPool, 2), chars, 5, usedDigitalByCustomer)));
        // Order 4: Secondary LED (PHYSICAL)
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 3),
                resolveCurrentMonthInstant(today, 0.48, 10), OrderType.PHYSICAL,
                buildItems(item(led1, 2)));
        // Order 5: Subscription qty=1 + unique characters qty=1 (DIGITAL)
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 1),
                resolveCurrentMonthInstant(today, 0.60, 15), OrderType.DIGITAL,
                buildSalesItems(pickCustomer(customerPool, 1), usedDigitalByCustomer, led0,
                        uniqueSingleProduct(pickCustomer(customerPool, 1), sub0, usedDigitalByCustomer, null),
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 6, usedDigitalByCustomer),
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 7, usedDigitalByCustomer)));
        // Order 6: Primary LED + unique characters qty=1 (MIXED)
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 2),
                resolveCurrentMonthInstant(today, 0.72, 16), OrderType.MIXED,
                buildSalesItems(pickCustomer(customerPool, 2), usedDigitalByCustomer, led0,
                        item(led0, 1),
                        uniqueDigitalItem(pickCustomer(customerPool, 2), chars, 1, usedDigitalByCustomer),
                        uniqueDigitalItem(pickCustomer(customerPool, 2), chars, 2, usedDigitalByCustomer)));
        // Order 7: LEDs for primary customer (PHYSICAL)
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 0),
                resolveCurrentMonthInstant(today, 0.84, 13), OrderType.PHYSICAL,
                buildItems(item(led2, 1), item(led1, 1)));
        // Order 8: Digital characters qty=1 + Subscription qty=1 (DIGITAL)
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 3),
                resolveCurrentMonthInstant(today, 0.94, 17), OrderType.DIGITAL,
                buildSalesItems(pickCustomer(customerPool, 3), usedDigitalByCustomer, led0,
                        uniqueDigitalItem(pickCustomer(customerPool, 3), chars, 0, usedDigitalByCustomer),
                        uniqueDigitalItem(pickCustomer(customerPool, 3), chars, 3, usedDigitalByCustomer),
                        uniqueSingleProduct(pickCustomer(customerPool, 3), sub0, usedDigitalByCustomer, null)));

        // ==================== 1 MONTH AGO (7 orders) ====================
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 1),
                resolvePastMonthInstant(prev1Ym, 2, 10), OrderType.MIXED,
                buildSalesItems(pickCustomer(customerPool, 1), usedDigitalByCustomer, led0,
                        item(led0, 1),
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 1, usedDigitalByCustomer),
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 4, usedDigitalByCustomer)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 2),
                resolvePastMonthInstant(prev1Ym, 5, 14), OrderType.DIGITAL,
                buildSalesItems(pickCustomer(customerPool, 2), usedDigitalByCustomer, led0,
                        uniqueDigitalItem(pickCustomer(customerPool, 2), chars, 3, usedDigitalByCustomer),
                        uniqueDigitalItem(pickCustomer(customerPool, 2), chars, 4, usedDigitalByCustomer),
                        uniqueSingleProduct(pickCustomer(customerPool, 2), sub0, usedDigitalByCustomer, null)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 3),
                resolvePastMonthInstant(prev1Ym, 9, 11), OrderType.PHYSICAL,
                buildItems(item(led1, 2)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 0),
                resolvePastMonthInstant(prev1Ym, 13, 15), OrderType.MIXED,
                buildSalesItems(pickCustomer(customerPool, 0), usedDigitalByCustomer, led0,
                        item(led0, 1),
                        uniqueDigitalItem(pickCustomer(customerPool, 0), chars, 2, usedDigitalByCustomer)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 1),
                resolvePastMonthInstant(prev1Ym, 17, 9), OrderType.DIGITAL,
                buildSalesItems(pickCustomer(customerPool, 1), usedDigitalByCustomer, led0,
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 5, usedDigitalByCustomer),
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 8, usedDigitalByCustomer)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 2),
                resolvePastMonthInstant(prev1Ym, 21, 16), OrderType.PHYSICAL,
                buildItems(item(led0, 1), item(led1, 1)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 3),
                resolvePastMonthInstant(prev1Ym, 25, 13), OrderType.DIGITAL,
                buildSalesItems(pickCustomer(customerPool, 3), usedDigitalByCustomer, led0,
                        uniqueDigitalItem(pickCustomer(customerPool, 3), chars, 8, usedDigitalByCustomer),
                        uniqueSingleProduct(pickCustomer(customerPool, 3), sub1, usedDigitalByCustomer, null)));

        // ==================== 2 MONTHS AGO (5 orders) ====================
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 0),
                resolvePastMonthInstant(prev2Ym, 3, 10), OrderType.PHYSICAL,
                buildItems(item(led0, 1), item(led2, 1)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 2),
                resolvePastMonthInstant(prev2Ym, 8, 14), OrderType.PHYSICAL,
                buildSalesItems(pickCustomer(customerPool, 2), usedDigitalByCustomer, led1,
                        uniqueSingleProduct(pickCustomer(customerPool, 2), bundle1, usedDigitalByCustomer, led1)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 1),
                resolvePastMonthInstant(prev2Ym, 14, 11), OrderType.DIGITAL,
                buildSalesItems(pickCustomer(customerPool, 1), usedDigitalByCustomer, led0,
                        uniqueDigitalItem(pickCustomer(customerPool, 1), chars, 9, usedDigitalByCustomer),
                        uniqueSingleProduct(pickCustomer(customerPool, 1), sub1, usedDigitalByCustomer, null)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 3),
                resolvePastMonthInstant(prev2Ym, 19, 15), OrderType.PHYSICAL,
                buildItems(item(led1, 1)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 0),
                resolvePastMonthInstant(prev2Ym, 24, 12), OrderType.PHYSICAL,
                buildItems(item(led2, 2)));

        // ==================== 3 MONTHS AGO (4 orders) ====================
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 1),
                resolvePastMonthInstant(prev3Ym, 4, 10), OrderType.PHYSICAL,
                buildItems(item(led0, 1)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 3),
                resolvePastMonthInstant(prev3Ym, 11, 14), OrderType.DIGITAL,
                buildSalesItems(pickCustomer(customerPool, 3), usedDigitalByCustomer, led0,
                        uniqueDigitalItem(pickCustomer(customerPool, 3), chars, 1, usedDigitalByCustomer),
                        uniqueDigitalItem(pickCustomer(customerPool, 3), chars, 4, usedDigitalByCustomer)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 2),
                resolvePastMonthInstant(prev3Ym, 18, 11), OrderType.MIXED,
                buildSalesItems(pickCustomer(customerPool, 2), usedDigitalByCustomer, led1,
                        item(led1, 1),
                        uniqueDigitalItem(pickCustomer(customerPool, 2), chars, 6, usedDigitalByCustomer)));
        seq = seedHistoricalSalesOrder(seq, pickCustomer(customerPool, 0),
                resolvePastMonthInstant(prev3Ym, 23, 16), OrderType.PHYSICAL,
                buildItems(item(led0, 1), item(led1, 1)));

        log.info("Seeded {} multi-month dashboard sales orders spanning {} through {}.",
                seq - 1, prev3Ym, currentYm);
    }

    private List<CustomerProfileSnapshot> resolveSalesCustomerPool(CustomerProfileSnapshot primaryCustomer) {
        List<CustomerProfileSnapshot> pool = new ArrayList<>();
        pool.add(primaryCustomer);
        customerLookupService.findProfileByEmail(SEED_CUSTOMER_2_EMAIL).ifPresent(pool::add);
        customerLookupService.findProfileByEmail(SEED_CUSTOMER_3_EMAIL).ifPresent(pool::add);
        customerLookupService.findProfileByEmail(SEED_CUSTOMER_4_EMAIL).ifPresent(pool::add);
        return pool;
    }

    private CustomerProfileSnapshot pickCustomer(List<CustomerProfileSnapshot> pool, int index) {
        return pool.get(index % pool.size());
    }

    private ProductSnapshot pickAt(List<ProductSnapshot> list, int index) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(index % list.size());
    }

    private ItemSeed item(ProductSnapshot product, int quantity) {
        if (product == null || product.productId() == null || product.basePrice() == null || quantity <= 0) {
            return null;
        }
        boolean isCharacter = product.productType() == ProductType.CHARACTER;
        int effectiveQty = (product.productType() != ProductType.LED) ? 1 : quantity;
        return new ItemSeed(product.productId(), effectiveQty, product.basePrice(), isCharacter);
    }

    private ItemSeed uniqueSingleProduct(
            CustomerProfileSnapshot customer,
            ProductSnapshot product,
            Map<Long, Set<Long>> usedDigitalByCustomer,
            ProductSnapshot fallbackPhysical) {
        if (product != null && product.productId() != null && product.basePrice() != null) {
            Set<Long> used = usedDigitalByCustomer.computeIfAbsent(customer.customerId(), ignored -> new HashSet<>());
            if (used.add(product.productId())) {
                return item(product, 1);
            }
        }
        return item(fallbackPhysical, 1);
    }

    private ItemSeed uniqueDigitalItem(
            CustomerProfileSnapshot customer,
            List<ProductSnapshot> chars,
            int preferredIndex,
            Map<Long, Set<Long>> usedDigitalByCustomer) {
        if (chars == null || chars.isEmpty()) {
            return null;
        }
        Set<Long> used = usedDigitalByCustomer.computeIfAbsent(customer.customerId(), ignored -> new HashSet<>());
        for (int offset = 0; offset < chars.size(); offset++) {
            ProductSnapshot candidate = chars.get((preferredIndex + offset) % chars.size());
            if (candidate != null && candidate.productId() != null && candidate.basePrice() != null
                    && used.add(candidate.productId())) {
                return new ItemSeed(candidate.productId(), 1, candidate.basePrice(), true);
            }
        }
        return null;
    }

    private List<ItemSeed> buildSalesItems(
            CustomerProfileSnapshot customer,
            Map<Long, Set<Long>> usedDigitalByCustomer,
            ProductSnapshot fallbackLed,
            ItemSeed... candidates) {
        List<ItemSeed> result = buildItems(candidates);
        if (result.isEmpty() && fallbackLed != null) {
            ItemSeed fallback = item(fallbackLed, 1);
            if (fallback != null) {
                result.add(fallback);
            }
        }
        return result;
    }

    private List<ItemSeed> buildItems(ItemSeed... candidates) {
        List<ItemSeed> result = new ArrayList<>();
        for (ItemSeed candidate : candidates) {
            if (candidate != null) {
                result.add(candidate);
            }
        }
        return result;
    }

    private Instant resolveCurrentMonthInstant(LocalDate today, double ratio, int hourUtc) {
        int maxDay = Math.max(1, today.getDayOfMonth());
        int day = Math.max(1, Math.min(maxDay, (int) Math.round(1 + (maxDay - 1) * ratio)));
        return YearMonth.from(today).atDay(day).atTime(hourUtc, 15).toInstant(ZoneOffset.UTC);
    }

    private Instant resolvePastMonthInstant(YearMonth ym, int dayOfMonth, int hourUtc) {
        int clampedDay = Math.min(dayOfMonth, ym.lengthOfMonth());
        return ym.atDay(clampedDay).atTime(hourUtc, 30).toInstant(ZoneOffset.UTC);
    }

    private int seedHistoricalSalesOrder(
            int seq,
            CustomerProfileSnapshot customer,
            Instant orderCreatedAt,
            OrderType orderType,
            List<ItemSeed> items) {
        if (items.isEmpty()) {
            return seq;
        }
        BigDecimal totalAmount = items.stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String codeSuffix = String.format("SALES%03d", seq);
        String recipientName = customer.fullName() != null && !customer.fullName().isBlank()
                ? customer.fullName() : "Lumintra Customer";
        String recipientPhone = customer.phone() != null && !customer.phone().isBlank()
                ? customer.phone() : "0901234567";

        ShippingSeed shipping = (orderType == OrderType.DIGITAL)
                ? null
                : new ShippingSeed(
                        recipientName,
                        recipientPhone,
                        "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh",
                        new BigDecimal("10.7768890"),
                        new BigDecimal("106.7008060"),
                        DeliveryStatus.DELIVERED,
                        "AHA-LMT-" + codeSuffix,
                        "https://ahamove.com/tracking/AHA-LMT-" + codeSuffix,
                        new BigDecimal("25000.00"),
                        null,
                        orderCreatedAt.plus(2, ChronoUnit.DAYS)
                );

        OrderEntity savedOrder = createOrderWithMultipleItems(
                customer.customerId(),
                orderType,
                OrderStatus.COMPLETED,
                totalAmount,
                orderCreatedAt.plus(1, ChronoUnit.HOURS),
                items,
                shipping,
                new PaymentSeed(codeSuffix, PaymentStatus.SUCCESS, orderCreatedAt.plus(10, ChronoUnit.MINUTES))
        );
        orderRepository.updateTimestampsForSeed(savedOrder.getOrderId(), orderCreatedAt);
        return seq + 1;
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
        Long subscriptionRecipientId = customerLookupService.findProfileByEmail(SEED_CUSTOMER_2_EMAIL)
                .map(CustomerProfileSnapshot::customerId)
                .orElse(customerId);

        if (!customerSubscriptionRepository.existsByCustomerIdAndStatus(subscriptionRecipientId, "ACTIVE")) {
            CustomerSubscriptionEntity subscription = CustomerSubscriptionEntity.createActive(
                    subscriptionRecipientId, products.subscriptionId(), null);
            customerSubscriptionRepository.save(subscription);
            log.info("Seeded active customer subscription [customerId={}] [productId={}]",
                    subscriptionRecipientId, products.subscriptionId());
        }

        Long ownedVersionId = products.ownedCharacterVersionId();
        if (ownedVersionId == null) {
            log.info("No separate seeded character version available for owned library. Skipping owned character seeding.");
            return;
        }

        if (!ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, ownedVersionId)) {
            OwnedCharacterEntity owned = OwnedCharacterEntity.create(customerId, ownedVersionId, null);
            ownedCharacterRepository.save(owned);
            log.info("Seeded active owned character [customerId={}] [versionId={}]", customerId, ownedVersionId);
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
                List.of(new ItemSeed(productId, quantity, unitPrice, false)),
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

        boolean shouldGrantDigital = (status == OrderStatus.COMPLETED || status == OrderStatus.PARTIALLY_FULFILLED)
                && payment != null
                && payment.status() == PaymentStatus.SUCCESS;

        for (ItemSeed item : items) {
            OrderItemEntity savedItem = orderItemRepository.save(OrderItemEntity.create(
                    savedOrder, item.productId(), item.quantity(), item.unitPrice()));
            if (shouldGrantDigital && item.isCharacter()) {
                characterLookupService.findVersionIdByProductId(item.productId()).ifPresent(versionId -> {
                    if (!ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, versionId)) {
                        Long orderItemId = savedItem != null ? savedItem.getOrderItemId() : null;
                        ownedCharacterRepository.save(OwnedCharacterEntity.create(customerId, versionId, orderItemId));
                    }
                });
            }
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
                delivery.setDeliveryAttemptCount(1);
            }
            if (shipping.deliveryStatus() == DeliveryStatus.FAILED) {
                delivery.setFailureReason("Không liên lạc được với người nhận sau 3 cuộc gọi (AhaMove)");
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
            Long secondaryPhysicalId, BigDecimal secondaryPhysicalPrice,
            Long digitalId, BigDecimal digitalPrice,
            Long subscriptionId, BigDecimal subscriptionPrice,
            Long ownedCharacterVersionId,
            List<ProductSnapshot> ledProducts,
            List<ProductSnapshot> characterProducts,
            List<ProductSnapshot> bundleProducts,
            List<ProductSnapshot> subscriptionProducts
    ) {}

    private record ItemSeed(Long productId, int quantity, BigDecimal unitPrice, boolean isCharacter) {
        private ItemSeed(Long productId, int quantity, BigDecimal unitPrice) {
            this(productId, quantity, unitPrice, false);
        }
    }

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
