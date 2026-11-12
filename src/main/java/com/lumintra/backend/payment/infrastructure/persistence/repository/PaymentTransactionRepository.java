package com.lumintra.backend.payment.infrastructure.persistence.repository;

import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("paymentSessionTransactionRepository")
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {

    Optional<PaymentTransactionEntity> findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(String transactionCode);

    Optional<PaymentTransactionEntity> findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(Long orderId);
}
