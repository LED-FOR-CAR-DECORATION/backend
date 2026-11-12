package com.lumintra.backend.payment.infrastructure.persistence.entity;

import com.lumintra.backend.payment.domain.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persistence entity mapped strictly to the finalized Supabase public.payment_transaction table.
 */
@Entity(name = "PaymentSessionTransactionEntity")
@Table(name = "payment_transaction")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransactionEntity {

    public static final String GATEWAY_SEPAY = "SEPAY";
    public static final String DB_STATUS_PENDING = "PENDING";
    public static final String DB_STATUS_SUCCESS = "SUCCESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "gateway", nullable = false, length = 30)
    private String gateway;

    @Column(name = "transaction_code", length = 100)
    private String transactionCode;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 30)
    private String status = PaymentStatus.PENDING.name();

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentStatus getPaymentStatus() {
        return PaymentStatus.fromDatabaseValue(this.status);
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.status = paymentStatus != null ? paymentStatus.name() : PaymentStatus.PENDING.name();
    }

    public static PaymentTransactionEntity createPending(
            Long targetId,
            String gateway,
            String transactionCode,
            BigDecimal amount) {
        PaymentTransactionEntity entity = new PaymentTransactionEntity();
        entity.setOrderId(targetId);
        entity.setGateway(gateway != null && !gateway.isBlank() ? gateway.trim() : GATEWAY_SEPAY);
        entity.setTransactionCode(transactionCode);
        entity.setAmount(amount);
        entity.setStatus(PaymentStatus.PENDING.name());
        return entity;
    }

    public static PaymentTransactionEntity of(
            Long paymentId,
            Long targetId,
            String gateway,
            String transactionCode,
            BigDecimal amount,
            PaymentStatus status,
            Instant paidAt,
            Instant createdAt) {
        PaymentTransactionEntity entity = createPending(targetId, gateway, transactionCode, amount);
        entity.setPaymentId(paymentId);
        entity.setPaymentStatus(status);
        entity.setPaidAt(paidAt);
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
