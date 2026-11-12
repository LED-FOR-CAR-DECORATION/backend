package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = StaffOrderStatusPersistenceTest.Config.class)
class StaffOrderStatusPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {OrderEntity.class, LedModelEntity.class})
    @EnableJpaRepositories(basePackageClasses = {OrderRepository.class, LedModelRepository.class})
    static class Config {}

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private LedModelRepository ledModelRepository;

    @Test
    @DisplayName("COM-12 UTCID01 [N]: OrderRepository.updateStatusIfCurrent atomically transitions status once and prevents concurrent double updates")
    void updateStatusIfCurrent_atomicCasUpdate_succeedsOnceAndRejectsStaleExpectedStatus() {
        OrderEntity order = OrderEntity.createPendingPayment(
                15L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.PHYSICAL,
                Instant.parse("2026-11-12T12:00:00Z")
        );
        entityManager.persist(order);
        entityManager.flush();
        Long orderId = order.getOrderId();

        Instant updatedAt = Instant.parse("2026-11-12T06:55:00Z");
        int firstAttempt = orderRepository.updateStatusIfCurrent(
                orderId,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELED,
                updatedAt
        );
        assertThat(firstAttempt).isEqualTo(1);

        int concurrentSecondAttempt = orderRepository.updateStatusIfCurrent(
                orderId,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.CANCELED,
                updatedAt.plusSeconds(5)
        );
        assertThat(concurrentSecondAttempt).isZero();

        OrderEntity reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("COM-12 UTCID02 [N]: LedModelRepository.restoreStock increments stockQuantity in DB and returns 0 when model is absent")
    void restoreStock_incrementsExistingModelAndReturnsZeroForMissingModel() {
        LedModelEntity model = LedModelEntity.create(501L, "Lumintra Matrix 64x32", Map.of("width", 64), 3L);
        entityManager.persist(model);
        entityManager.flush();
        entityManager.clear();

        int updatedRows = ledModelRepository.restoreStock(501L, 4L);
        assertThat(updatedRows).isEqualTo(1);

        int missingRows = ledModelRepository.restoreStock(999L, 2L);
        assertThat(missingRows).isZero();

        LedModelEntity reloaded = ledModelRepository.findById(501L).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(7L);
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }
}
