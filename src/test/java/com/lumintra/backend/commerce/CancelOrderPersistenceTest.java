package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = CancelOrderPersistenceTest.Config.class)
class CancelOrderPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {OrderEntity.class, OrderDeliveryEntity.class})
    @EnableJpaRepositories(basePackageClasses = {OrderRepository.class, OrderDeliveryRepository.class})
    static class Config {}

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDeliveryRepository orderDeliveryRepository;

    @Test
    @DisplayName("COM-09 UTCID01 [N]: Ownership lookup + atomic PENDING_PAYMENT->CANCELED update succeeds once and rejects concurrent duplicate update")
    void cancelOrderPersistence_atomicUpdateAndOwnershipCheck_pendingPaymentToCanceled() {
        OrderEntity order = OrderEntity.of(
                null,
                42L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PENDING_PAYMENT,
                Instant.parse("2026-11-12T12:00:00Z")
        );
        entityManager.persist(order);
        entityManager.flush();

        Long orderId = order.getOrderId();

        assertThat(orderRepository.findByOrderIdAndCustomerId(orderId, 42L)).isPresent();
        assertThat(orderRepository.findByOrderIdAndCustomerId(orderId, 999L)).isEmpty();

        Instant canceledAt = Instant.parse("2026-11-13T10:00:00Z");
        int firstAttempt = orderRepository.updateStatusIfCurrent(
                orderId,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELED,
                canceledAt
        );
        assertThat(firstAttempt).isEqualTo(1);

        int duplicateAttempt = orderRepository.updateStatusIfCurrent(
                orderId,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELED,
                canceledAt
        );
        assertThat(duplicateAttempt).isEqualTo(0);

        entityManager.clear();
        OrderEntity reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(canceledAt);
    }

    @Test
    @DisplayName("COM-09 UTCID02 [N]: Ownership lookup + atomic PROCESSING->REFUNDED update succeeds and preserves delivery record")
    void cancelOrderPersistence_atomicUpdateAndOwnershipCheck_processingToRefunded() {
        OrderEntity order = OrderEntity.of(
                null,
                42L,
                10L,
                new BigDecimal("350000.00"),
                new BigDecimal("30000.00"),
                new BigDecimal("380000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PROCESSING,
                Instant.parse("2026-11-12T12:00:00Z")
        );
        entityManager.persist(order);

        OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                order,
                new ResolvedShippingAddress(
                        10L,
                        "Nguyen Van B",
                        "0987654321",
                        "123 Le Loi, Da Nang",
                        new BigDecimal("16.0544"),
                        new BigDecimal("108.2022")
                )
        );
        entityManager.persist(delivery);
        entityManager.flush();

        Long orderId = order.getOrderId();

        Instant refundedAt = Instant.parse("2026-11-13T10:15:00Z");
        int updated = orderRepository.updateStatusIfCurrent(
                orderId,
                OrderStatus.PROCESSING,
                OrderStatus.REFUNDED,
                refundedAt
        );
        assertThat(updated).isEqualTo(1);

        entityManager.clear();
        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(reloadedOrder.getUpdatedAt()).isEqualTo(refundedAt);

        OrderDeliveryEntity reloadedDelivery = orderDeliveryRepository.findByOrder_OrderId(orderId).orElseThrow();
        assertThat(reloadedDelivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(reloadedDelivery.hasActiveShipment()).isFalse();
    }
}
