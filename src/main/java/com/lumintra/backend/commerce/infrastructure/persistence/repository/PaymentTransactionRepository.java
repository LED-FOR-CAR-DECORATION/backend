package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {

    Optional<PaymentTransactionEntity> findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(Long orderId);

    @Query("""
            SELECT new com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentSummaryRow(
                pt.orderId,
                pt.status
            )
            FROM PaymentTransactionEntity pt
            WHERE pt.orderId IN :orderIds
            ORDER BY pt.createdAt DESC, pt.paymentId DESC
            """)
    List<PaymentSummaryRow> findSummaryRowsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    boolean existsByTransactionCode(String transactionCode);

    boolean existsByTransactionCodeEndingWith(String suffix);

    List<PaymentTransactionEntity> findByTransactionCodeStartingWith(String prefix);
}
