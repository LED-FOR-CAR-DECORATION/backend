package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.StaffOrderFilterRequest;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliverySummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderSpecifications;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentSummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = StaffOrderQueryTest.Config.class)
class StaffOrderQueryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = OrderEntity.class)
    @EnableJpaRepositories(basePackageClasses = {
            OrderRepository.class,
            OrderDeliveryRepository.class,
            PaymentTransactionRepository.class
    })
    static class Config {}

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("orderId")
    );

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDeliveryRepository orderDeliveryRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    private OrderEntity persistOrder(Long customerId, OrderType orderType, OrderStatus status, boolean withDelivery, DeliveryStatus deliveryStatus) {
        OrderEntity order = OrderEntity.createPendingPayment(
                customerId,
                withDelivery ? 10L : null,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                orderType,
                Instant.parse("2026-11-12T10:00:00Z")
        );
        order.setStatus(status);
        entityManager.persist(order);

        if (withDelivery) {
            OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                    order,
                    new ResolvedShippingAddress(
                            10L,
                            "Recipient " + order.getOrderId(),
                            "0912345678",
                            "Saigon Hi-Tech Park",
                            new BigDecimal("10.8411000"),
                            new BigDecimal("106.8099000")
                    )
            );
            delivery.setDeliveryStatus(deliveryStatus);
            entityManager.persist(delivery);
        }

        entityManager.flush();
        return order;
    }

    private void persistPayment(Long orderId, PaymentStatus status, String code) {
        PaymentTransactionEntity tx = PaymentTransactionEntity.createPending(
                orderId,
                PaymentGateway.SEPAY,
                code,
                new BigDecimal("250000.00")
        );
        tx.setStatus(status);
        entityManager.persist(tx);
        entityManager.flush();
    }

    @Test
    @DisplayName("COM-10 [N]: AC01 default fulfillment queue includes only physical orders in PROCESSING or PARTIALLY_FULFILLED")
    void withStaffFulfillmentFilter_defaultQueue_excludesDigitalAndNonQueueStatuses() {
        OrderEntity processingLed = persistOrder(101L, OrderType.PHYSICAL, OrderStatus.PROCESSING, true, DeliveryStatus.PENDING);
        OrderEntity partialBundle = persistOrder(102L, OrderType.BUNDLE, OrderStatus.PARTIALLY_FULFILLED, true, DeliveryStatus.ASSIGNED);
        persistOrder(103L, OrderType.DIGITAL, OrderStatus.PROCESSING, false, null);
        persistOrder(104L, OrderType.PHYSICAL, OrderStatus.PENDING_PAYMENT, true, DeliveryStatus.PENDING);
        persistOrder(105L, OrderType.PHYSICAL, OrderStatus.SHIPPING, true, DeliveryStatus.SHIPPING);
        persistOrder(106L, OrderType.PHYSICAL, OrderStatus.COMPLETED, true, DeliveryStatus.DELIVERED);
        entityManager.clear();

        Page<OrderEntity> page = orderRepository.findAll(
                OrderSpecifications.withStaffFulfillmentFilter(new StaffOrderFilterRequest()),
                PageRequest.of(0, 10, DEFAULT_SORT)
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(OrderEntity::getOrderId)
                .containsExactlyInAnyOrder(partialBundle.getOrderId(), processingLed.getOrderId());
    }

    @Test
    @DisplayName("COM-10 [N]: AC02 filters combine explicit order status, deliveryStatus, and latest paymentStatus without duplicate rows")
    void withStaffFulfillmentFilter_combinedFilters_usesLatestPaymentTransactionOnly() {
        OrderEntity target = persistOrder(201L, OrderType.MIXED, OrderStatus.PENDING_PAYMENT, true, DeliveryStatus.PENDING);
        persistPayment(target.getOrderId(), PaymentStatus.FAILED, "TX-OLD-FAIL");
        persistPayment(target.getOrderId(), PaymentStatus.SUCCESS, "TX-NEW-OK");

        OrderEntity wrongLatestPayment = persistOrder(202L, OrderType.PHYSICAL, OrderStatus.PENDING_PAYMENT, true, DeliveryStatus.PENDING);
        persistPayment(wrongLatestPayment.getOrderId(), PaymentStatus.SUCCESS, "TX-OLD-OK");
        persistPayment(wrongLatestPayment.getOrderId(), PaymentStatus.FAILED, "TX-NEW-FAIL");

        OrderEntity wrongDeliveryStatus = persistOrder(203L, OrderType.PHYSICAL, OrderStatus.PENDING_PAYMENT, true, DeliveryStatus.PICKED_UP);
        persistPayment(wrongDeliveryStatus.getOrderId(), PaymentStatus.SUCCESS, "TX-OTHER");
        entityManager.clear();

        StaffOrderFilterRequest filter = new StaffOrderFilterRequest(
                OrderStatus.PENDING_PAYMENT,
                PaymentStatus.SUCCESS,
                DeliveryStatus.PENDING,
                0,
                10
        );

        Page<OrderEntity> page = orderRepository.findAll(
                OrderSpecifications.withStaffFulfillmentFilter(filter),
                PageRequest.of(0, 10, DEFAULT_SORT)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getOrderId()).isEqualTo(target.getOrderId());
    }

    @Test
    @DisplayName("COM-10 [B]: Batch summary projections return delivery snapshots and order latest-first payment statuses")
    void batchSummaryProjections_returnDeliveryAndOrderedPayments() {
        OrderEntity order = persistOrder(301L, OrderType.PHYSICAL, OrderStatus.PROCESSING, true, DeliveryStatus.PENDING);
        persistPayment(order.getOrderId(), PaymentStatus.FAILED, "TX-1");
        persistPayment(order.getOrderId(), PaymentStatus.SUCCESS, "TX-2");
        entityManager.clear();

        List<OrderDeliverySummaryRow> deliveryRows =
                orderDeliveryRepository.findSummaryRowsByOrderIds(List.of(order.getOrderId()));
        List<PaymentSummaryRow> paymentRows =
                paymentTransactionRepository.findSummaryRowsByOrderIds(List.of(order.getOrderId()));

        assertThat(deliveryRows).hasSize(1);
        assertThat(deliveryRows.getFirst().deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(deliveryRows.getFirst().recipientName()).isEqualTo("Recipient " + order.getOrderId());

        assertThat(paymentRows).hasSize(2);
        assertThat(paymentRows.getFirst().status()).isEqualTo(PaymentStatus.SUCCESS);
    }
}
