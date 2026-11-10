package com.lumintra.backend.commerce.infrastructure.persistence.entity;

import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 30)
    private PaymentGateway gateway;

    @Column(name = "transaction_code", length = 100)
    private String transactionCode;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PaymentTransactionEntity createPending(
            Long orderId,
            PaymentGateway gateway,
            String transactionCode,
            BigDecimal amount) {
        PaymentTransactionEntity entity = new PaymentTransactionEntity();
        entity.setOrderId(orderId);
        entity.setGateway(gateway);
        entity.setTransactionCode(transactionCode);
        entity.setAmount(amount);
        entity.setStatus(PaymentStatus.PENDING);
        return entity;
    }

    public static PaymentTransactionEntity of(
            Long paymentId,
            Long orderId,
            PaymentGateway gateway,
            String transactionCode,
            BigDecimal amount,
            PaymentStatus status) {
        PaymentTransactionEntity entity = createPending(orderId, gateway, transactionCode, amount);
        entity.setPaymentId(paymentId);
        entity.setStatus(status);
        return entity;
    }
}
