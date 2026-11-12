package com.lumintra.backend.payment;

import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = PaymentTransactionRepositoryTest.Config.class)
class PaymentTransactionRepositoryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = PaymentTransactionEntity.class)
    @EnableJpaRepositories(basePackageClasses = PaymentTransactionRepository.class)
    static class Config {}

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PaymentTransactionRepository repository;

    @Test
    @DisplayName("PAY-01 [N]: Persists and queries PaymentTransactionEntity by transactionCode and orderId")
    void findFirstByTransactionCodeAndOrderId_returnsLatestTransaction() {
        PaymentTransactionEntity first = PaymentTransactionEntity.createPending(
                102L,
                "SEPAY",
                "LUMORD102FIRST111",
                new BigDecimal("250000.00")
        );
        first.setPaymentStatus(PaymentStatus.EXPIRED);
        entityManager.persist(first);

        PaymentTransactionEntity latest = PaymentTransactionEntity.createPending(
                102L,
                "SEPAY",
                "LUMORD102SECOND22",
                new BigDecimal("250000.00")
        );
        entityManager.persist(latest);
        entityManager.flush();
        entityManager.clear();

        Optional<PaymentTransactionEntity> byCode =
                repository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc("LUMORD102SECOND22");
        Optional<PaymentTransactionEntity> byOrderId =
                repository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(102L);

        assertThat(byCode).isPresent();
        assertThat(byCode.get().getOrderId()).isEqualTo(102L);
        assertThat(byCode.get().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(byOrderId).isPresent();
        assertThat(byOrderId.get().getTransactionCode()).isEqualTo("LUMORD102SECOND22");
    }

    @Test
    @DisplayName("PAY-02 [N]: markAsSucceededIfPending transitions PENDING row to SUCCESS with paidAt and rejects second update")
    void markAsSucceededIfPending_updatesPendingOnce() {
        PaymentTransactionEntity pending = PaymentTransactionEntity.createPending(
                205L,
                "SEPAY",
                "LMTORD205TESTCODE",
                new BigDecimal("500000.00")
        );
        entityManager.persist(pending);
        entityManager.flush();
        Long paymentId = pending.getPaymentId();
        entityManager.clear();

        Instant paidAt = Instant.parse("2026-11-13T01:02:03Z");
        int firstUpdate = repository.markAsSucceededIfPending(
                paymentId,
                PaymentTransactionEntity.DB_STATUS_SUCCESS,
                paidAt
        );
        int secondUpdate = repository.markAsSucceededIfPending(
                paymentId,
                PaymentTransactionEntity.DB_STATUS_SUCCESS,
                paidAt.plusSeconds(10)
        );
        entityManager.clear();

        PaymentTransactionEntity reloaded = entityManager.find(PaymentTransactionEntity.class, paymentId);
        assertThat(firstUpdate).isEqualTo(1);
        assertThat(secondUpdate).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentTransactionEntity.DB_STATUS_SUCCESS);
        assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(reloaded.getPaidAt()).isEqualTo(paidAt);
    }
}
