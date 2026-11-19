package com.lumintra.backend.commission.infrastructure.persistence.entity;

import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Durable record of a customer revision brief. The {@code EXTRA_FEE_CARD} chat message is only
 * the conversational view of it; designers read the brief from this table.
 */
@Entity
@Table(
        name = "commission_revision_request",
        indexes = {
                @Index(name = "idx_commission_revision_request_commission_status", columnList = "commission_id, status"),
                @Index(name = "uk_commission_revision_request_message", columnList = "message_id", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommissionRevisionRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "revision_request_id")
    private Long revisionRequestId;

    @Column(name = "commission_id", nullable = false)
    private Long commissionId;

    /** The EXTRA_FEE_CARD chat message that presented this brief to the customer. */
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /** 1-based revision sequence for the commission (free and paid revisions share the counter). */
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "uses_free_quota", nullable = false)
    private boolean usesFreeQuota;

    @Column(name = "revision_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal revisionFee = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private CommissionRevisionRequestStatus status = CommissionRevisionRequestStatus.PENDING;

    @Column(name = "payment_code", length = 100)
    private String paymentCode;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static CommissionRevisionRequestEntity open(
            Long commissionId,
            Long messageId,
            int revisionNumber,
            String description,
            boolean usesFreeQuota,
            BigDecimal revisionFee) {
        return CommissionRevisionRequestEntity.builder()
                .commissionId(commissionId)
                .messageId(messageId)
                .revisionNumber(Math.max(1, revisionNumber))
                .description(description)
                .usesFreeQuota(usesFreeQuota)
                .revisionFee(revisionFee)
                .status(CommissionRevisionRequestStatus.PENDING)
                .build();
    }

    public boolean isPending() {
        return status == CommissionRevisionRequestStatus.PENDING;
    }

    /** CMS-20: customer confirms a revision covered by free quota. */
    public void confirmFree(int revisionNumber, Instant now) {
        this.revisionNumber = Math.max(1, revisionNumber);
        this.usesFreeQuota = true;
        this.revisionFee = BigDecimal.ZERO.setScale(2);
        this.paymentCode = null;
        this.status = CommissionRevisionRequestStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    /** CMS-20: customer confirms a paid revision; checkout is opened. */
    public void awaitPayment(int revisionNumber, BigDecimal fee, String paymentCode, Instant now) {
        this.revisionNumber = Math.max(1, revisionNumber);
        this.usesFreeQuota = false;
        this.revisionFee = fee;
        this.paymentCode = paymentCode;
        this.status = CommissionRevisionRequestStatus.AWAITING_PAYMENT;
        this.confirmedAt = now;
    }

    /** CMS-21: extra revision fee settled. */
    public void markPaid(Instant paidAt) {
        this.status = CommissionRevisionRequestStatus.CONFIRMED;
        this.paidAt = paidAt;
    }

    /** CMS-22 / payment expiry: checkout abandoned, brief can be confirmed again. */
    public void reopen() {
        this.status = CommissionRevisionRequestStatus.PENDING;
        this.paymentCode = null;
        this.confirmedAt = null;
    }

    public void supersede() {
        this.status = CommissionRevisionRequestStatus.SUPERSEDED;
    }

    /** A preview answering this revision was delivered to the customer. */
    public void complete(Instant now) {
        this.status = CommissionRevisionRequestStatus.COMPLETED;
        this.completedAt = now;
    }
}
