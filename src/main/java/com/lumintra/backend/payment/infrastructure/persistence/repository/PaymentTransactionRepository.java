package com.lumintra.backend.payment.infrastructure.persistence.repository;

import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository("paymentSessionTransactionRepository")
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {

    Optional<PaymentTransactionEntity> findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(String transactionCode);

    Optional<PaymentTransactionEntity> findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(Long orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PaymentSessionTransactionEntity pt
            SET pt.status = :targetStatus,
                pt.paidAt = :paidAt
            WHERE pt.paymentId = :paymentId
              AND UPPER(pt.status) = 'PENDING'
            """)
    int markAsSucceededIfPending(
            @Param("paymentId") Long paymentId,
            @Param("targetStatus") String targetStatus,
            @Param("paidAt") Instant paidAt);
}
