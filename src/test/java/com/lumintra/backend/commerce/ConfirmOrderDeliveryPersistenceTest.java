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
@ContextConfiguration(classes = ConfirmOrderDeliveryPersistenceTest.Config.class)
class ConfirmOrderDeliveryPersistenceTest {

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
    @DisplayName("COM-15 UTCID01 [N]: Ownership lookup + atomic DELIVERED->COMPLETED update succeeds once and preserves carrier delivery timestamps")
    void confirmDeliveryPersistence_atomicUpdateAndOwnershipCheck_preserveDeliveryAuditFields() {
        OrderEntity order = OrderEntity.of(
                null,
                42L,
                10L,
                new BigDecimal("250000.00"),
                new BigDecimal("33000.00"),
                new BigDecimal("283000.00"),
                OrderType.PHYSICAL,
                OrderStatus.DELIVERED,
                Instant.parse("2026-11-12T12:00:00Z")
        );
        entityManager.persist(order);

        Instant carrierDeliveredAt = Instant.parse("2026-11-12T13:15:00Z");
        OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                order,
                new ResolvedShippingAddress(
                        10L,
                        "Dang Phuoc Nguyen",
                        "0912345678",
                        "475A Dien Bien Phu, Binh Thanh, HCMC",
                        new BigDecimal("10.8018493"),
                        new BigDecimal("106.7144660")
                )
        );
        delivery.setDeliveryStatus(DeliveryStatus.DELIVERED);
        delivery.setTrackingNumber("24ABCD");
        delivery.setDeliveredAt(carrierDeliveredAt);
        entityManager.persist(delivery);
        entityManager.flush();

        Long orderId = order.getOrderId();

        assertThat(orderRepository.findByOrderIdAndCustomerId(orderId, 42L)).isPresent();
        assertThat(orderRepository.findByOrderIdAndCustomerId(orderId, 999L)).isEmpty();

        Instant confirmedAt = Instant.parse("2026-11-12T14:25:00Z");
        int firstAttempt = orderRepository.updateStatusIfCurrent(
                orderId,
                OrderStatus.DELIVERED,
                OrderStatus.COMPLETED,
                confirmedAt
        );
        assertThat(firstAttempt).isEqualTo(1);

        int duplicateAttempt = orderRepository.updateStatusIfCurrent(
                orderId,
                OrderStatus.DELIVERED,
                OrderStatus.COMPLETED,
                confirmedAt.plusSeconds(10)
        );
        assertThat(duplicateAttempt).isZero();

        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(reloadedOrder.getUpdatedAt()).isEqualTo(confirmedAt);

        OrderDeliveryEntity reloadedDelivery = orderDeliveryRepository.findByOrder_OrderId(orderId).orElseThrow();
        assertThat(reloadedDelivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(reloadedDelivery.getDeliveredAt()).isEqualTo(carrierDeliveredAt);
    }
}
