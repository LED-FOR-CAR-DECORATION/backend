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
@ContextConfiguration(classes = DispatchOrderPersistenceTest.Config.class)
class DispatchOrderPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {OrderEntity.class, OrderDeliveryEntity.class})
    @EnableJpaRepositories(basePackageClasses = {OrderRepository.class, OrderDeliveryRepository.class})
    static class Config {}

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderDeliveryRepository orderDeliveryRepository;

    private static final Set<DeliveryStatus> ELIGIBLE_STATUSES = Set.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.FAILED,
            DeliveryStatus.RETURNED
    );

    @Test
    @DisplayName("COM-13 UTCID01 [N]: OrderDeliveryRepository.updateDispatchInfoIfEligible updates tracking metadata once and rejects concurrent second dispatch")
    void updateDispatchInfoIfEligible_atomicCasUpdate_succeedsOnceAndRejectsActiveShipment() {
        OrderEntity order = OrderEntity.of(
                null,
                15L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PROCESSING,
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
        entityManager.persist(delivery);
        entityManager.flush();
        Long orderId = order.getOrderId();

        Instant updatedAt = Instant.parse("2026-11-12T08:30:00Z");
        int firstAttempt = orderDeliveryRepository.updateDispatchInfoIfEligible(
                orderId,
                ELIGIBLE_STATUSES,
                DeliveryStatus.ASSIGNED,
                "24ABCD",
                "https://expressstg.ahamove.com/s/24ABCD",
                new BigDecimal("33000.00"),
                updatedAt
        );
        assertThat(firstAttempt).isEqualTo(1);

        int concurrentSecondAttempt = orderDeliveryRepository.updateDispatchInfoIfEligible(
                orderId,
                ELIGIBLE_STATUSES,
                DeliveryStatus.ASSIGNED,
                "24EFGH",
                "https://expressstg.ahamove.com/s/24EFGH",
                new BigDecimal("35000.00"),
                updatedAt.plusSeconds(10)
        );
        assertThat(concurrentSecondAttempt).isZero();

        OrderDeliveryEntity reloaded = orderDeliveryRepository.findByOrder_OrderId(orderId).orElseThrow();
        assertThat(reloaded.getDeliveryStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(reloaded.getTrackingNumber()).isEqualTo("24ABCD");
        assertThat(reloaded.getTrackingUrl()).isEqualTo("https://expressstg.ahamove.com/s/24ABCD");
        assertThat(reloaded.getCarrierFee()).isEqualByComparingTo("33000.00");
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("COM-13 UTCID02 [N]: FAILED and RETURNED deliveries remain eligible for carrier re-dispatch while DELIVERED is rejected")
    void updateDispatchInfoIfEligible_allowsFailedAndReturnedAndBlocksDelivered() {
        OrderEntity failedOrder = OrderEntity.of(
                null,
                16L,
                11L,
                new BigDecimal("300000.00"),
                BigDecimal.ZERO,
                new BigDecimal("300000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PROCESSING,
                Instant.parse("2026-11-12T12:00:00Z")
        );
        entityManager.persist(failedOrder);

        OrderDeliveryEntity failedDelivery = OrderDeliveryEntity.createPending(
                failedOrder,
                new ResolvedShippingAddress(
                        11L,
                        "Nguyen Van B",
                        "0909888777",
                        "123 Nguyen Hue, District 1, HCMC",
                        new BigDecimal("10.7756587"),
                        new BigDecimal("106.7004238")
                )
        );
        failedDelivery.setDeliveryStatus(DeliveryStatus.FAILED);
        entityManager.persist(failedDelivery);

        OrderEntity deliveredOrder = OrderEntity.of(
                null,
                17L,
                12L,
                new BigDecimal("400000.00"),
                BigDecimal.ZERO,
                new BigDecimal("400000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PROCESSING,
                Instant.parse("2026-11-12T12:00:00Z")
        );
        entityManager.persist(deliveredOrder);

        OrderDeliveryEntity deliveredDelivery = OrderDeliveryEntity.createPending(
                deliveredOrder,
                new ResolvedShippingAddress(
                        12L,
                        "Le Thi C",
                        "0911222333",
                        "7/28 Thanh Thai, District 10, HCMC",
                        new BigDecimal("10.7697535"),
                        new BigDecimal("106.6636615")
                )
        );
        deliveredDelivery.setDeliveryStatus(DeliveryStatus.DELIVERED);
        entityManager.persist(deliveredDelivery);
        entityManager.flush();

        Instant updatedAt = Instant.parse("2026-11-12T08:40:00Z");
        int redispatchResult = orderDeliveryRepository.updateDispatchInfoIfEligible(
                failedOrder.getOrderId(),
                ELIGIBLE_STATUSES,
                DeliveryStatus.ASSIGNED,
                "24RETRY",
                "https://expressstg.ahamove.com/s/24RETRY",
                new BigDecimal("28000.00"),
                updatedAt
        );
        assertThat(redispatchResult).isEqualTo(1);

        int blockedDeliveredResult = orderDeliveryRepository.updateDispatchInfoIfEligible(
                deliveredOrder.getOrderId(),
                ELIGIBLE_STATUSES,
                DeliveryStatus.ASSIGNED,
                "24BLOCKED",
                "https://expressstg.ahamove.com/s/24BLOCKED",
                new BigDecimal("28000.00"),
                updatedAt
        );
        assertThat(blockedDeliveredResult).isZero();
    }
}
