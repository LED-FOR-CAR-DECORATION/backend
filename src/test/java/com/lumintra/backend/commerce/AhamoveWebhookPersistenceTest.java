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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = AhamoveWebhookPersistenceTest.Config.class)
class AhamoveWebhookPersistenceTest {

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

    private OrderDeliveryEntity persistOrderWithDelivery(
            Long customerId,
            String trackingNumber,
            OrderStatus orderStatus,
            DeliveryStatus deliveryStatus) {
        OrderEntity order = OrderEntity.of(
                null,
                customerId,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.PHYSICAL,
                orderStatus,
                Instant.parse("2026-11-12T12:00:00Z")
        );
        entityManager.persist(order);

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
        delivery.setTrackingNumber(trackingNumber);
        delivery.setDeliveryStatus(deliveryStatus);
        entityManager.persist(delivery);
        entityManager.flush();
        return delivery;
    }

    @Test
    @DisplayName("COM-14 UTCID01 [N]: Looks up delivery by trackingNumber and atomically advances PENDING -> ASSIGNED -> SHIPPING -> DELIVERED")
    void webhookAtomicUpdates_advanceDeliveryAndOrderMonotonically() {
        OrderDeliveryEntity delivery = persistOrderWithDelivery(
                15L,
                "26AHV001",
                OrderStatus.PROCESSING,
                DeliveryStatus.PENDING
        );
        Long orderId = delivery.getOrder().getOrderId();

        OrderDeliveryEntity found = orderDeliveryRepository
                .findFirstByTrackingNumberOrderByDeliveryIdDesc("26AHV001")
                .orElseThrow();
        assertThat(found.getDeliveryId()).isEqualTo(delivery.getDeliveryId());

        Instant t1 = Instant.parse("2026-11-12T09:00:00Z");
        assertThat(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                delivery.getDeliveryId(),
                Set.of(DeliveryStatus.PENDING),
                DeliveryStatus.ASSIGNED,
                "https://expressstg.ahamove.com/s/26AHV001",
                new BigDecimal("32000.00"),
                null,
                t1
        )).isEqualTo(1);
        assertThat(orderRepository.updateStatusIfIn(
                orderId,
                Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED),
                OrderStatus.SHIPPING,
                t1
        )).isEqualTo(1);

        Instant t2 = Instant.parse("2026-11-12T09:10:00Z");
        assertThat(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                delivery.getDeliveryId(),
                Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP),
                DeliveryStatus.SHIPPING,
                null,
                null,
                null,
                t2
        )).isEqualTo(1);

        Instant t3 = Instant.parse("2026-11-12T09:30:00Z");
        assertThat(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                delivery.getDeliveryId(),
                Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.SHIPPING),
                DeliveryStatus.DELIVERED,
                null,
                new BigDecimal("35000.00"),
                t3,
                t3
        )).isEqualTo(1);
        assertThat(orderRepository.updateStatusIfIn(
                orderId,
                Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED, OrderStatus.SHIPPING),
                OrderStatus.DELIVERED,
                t3
        )).isEqualTo(1);

        OrderDeliveryEntity reloadedDelivery = orderDeliveryRepository.findById(delivery.getDeliveryId()).orElseThrow();
        OrderEntity reloadedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloadedDelivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(reloadedDelivery.getTrackingUrl()).isEqualTo("https://expressstg.ahamove.com/s/26AHV001");
        assertThat(reloadedDelivery.getCarrierFee()).isEqualByComparingTo("35000.00");
        assertThat(reloadedDelivery.getDeliveredAt()).isEqualTo(t3);
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("COM-14 UTCID02 [A]: Rejects backward regressions and preserves terminal COMPLETED/CANCELED/REFUNDED orders while allowing FAILED -> RETURNED")
    void webhookAtomicUpdates_blockRegressionsAndTerminalOrderOverwrites() {
        OrderDeliveryEntity completedDelivery = persistOrderWithDelivery(
                16L,
                "26AHVTERM",
                OrderStatus.COMPLETED,
                DeliveryStatus.DELIVERED
        );
        Long completedOrderId = completedDelivery.getOrder().getOrderId();

        Instant now = Instant.parse("2026-11-12T10:00:00Z");
        assertThat(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                completedDelivery.getDeliveryId(),
                Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.SHIPPING),
                DeliveryStatus.FAILED,
                null,
                null,
                null,
                now
        )).isZero();
        assertThat(orderRepository.updateStatusIfIn(
                completedOrderId,
                Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED, OrderStatus.SHIPPING),
                OrderStatus.DELIVERY_FAILED,
                now
        )).isZero();

        OrderDeliveryEntity failedDelivery = persistOrderWithDelivery(
                17L,
                "26AHVFAIL",
                OrderStatus.DELIVERY_FAILED,
                DeliveryStatus.FAILED
        );
        assertThat(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                failedDelivery.getDeliveryId(),
                Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.SHIPPING, DeliveryStatus.FAILED),
                DeliveryStatus.RETURNED,
                null,
                null,
                null,
                now
        )).isEqualTo(1);

        OrderDeliveryEntity reloadedFailed = orderDeliveryRepository.findById(failedDelivery.getDeliveryId()).orElseThrow();
        assertThat(reloadedFailed.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETURNED);
    }
}
