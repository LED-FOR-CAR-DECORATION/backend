package com.lumintra.backend.commission.infrastructure.persistence.entity;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
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
import java.util.Objects;

@Entity
@Table(
        name = "commission",
        indexes = {
                @Index(name = "idx_commission_customer_created", columnList = "customer_id, created_at"),
                @Index(name = "idx_commission_status_created", columnList = "status, created_at"),
                @Index(name = "idx_commission_assigned_staff", columnList = "assigned_staff_id"),
                @Index(name = "idx_commission_assigned_designer", columnList = "assigned_designer_id")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommissionRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "commission_id")
    private Long commissionId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tier_code", nullable = false, length = 50)
    private String tierCode;

    @Column(name = "target_matrix_dimensions", length = 50)
    private String targetMatrixDimensions;

    @Column(name = "price_breakdown_json", columnDefinition = "TEXT")
    private String priceBreakdownJson;

    @Column(name = "initial_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal initialPrice = BigDecimal.ZERO;

    @Column(name = "extra_revision_fee", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal extraRevisionFee = BigDecimal.ZERO;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(name = "revisions_used", nullable = false)
    @Builder.Default
    private int revisionsUsed = 0;

    @Column(name = "max_revisions", nullable = false)
    @Builder.Default
    private int maxRevisions = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private CommissionStatus status = CommissionStatus.PENDING_REVIEW;

    @Column(name = "assigned_staff_id")
    private Long assignedStaffId;

    @Column(name = "assigned_designer_id")
    private Long assignedDesignerId;

    @Column(name = "active_payment_code", length = 100)
    private String activePaymentCode;

    @Column(name = "approved_submission_id")
    private Long approvedSubmissionId;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "quote_expires_at")
    private Instant quoteExpiresAt;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "auto_complete_at")
    private Instant autoCompleteAt;

    @Column(name = "is_sla_overdue", nullable = false)
    @Builder.Default
    private boolean isSlaOverdue = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static CommissionRequestEntity createPendingReview(
            Long customerId,
            String title,
            String description,
            String tierCode,
            String targetMatrixDimensions,
            String priceBreakdownJson,
            BigDecimal initialPrice,
            int maxRevisions,
            Long assignedStaffId) {
        BigDecimal resolvedInitialPrice = initialPrice != null ? initialPrice : BigDecimal.ZERO;
        return CommissionRequestEntity.builder()
                .customerId(customerId)
                .title(title != null ? title.trim() : "")
                .description(description)
                .tierCode(tierCode != null ? tierCode.trim() : "")
                .targetMatrixDimensions(targetMatrixDimensions)
                .priceBreakdownJson(priceBreakdownJson)
                .initialPrice(resolvedInitialPrice)
                .extraRevisionFee(BigDecimal.ZERO)
                .totalPrice(resolvedInitialPrice)
                .revisionsUsed(0)
                .maxRevisions(maxRevisions)
                .status(CommissionStatus.PENDING_REVIEW)
                .assignedStaffId(assignedStaffId)
                .isSlaOverdue(false)
                .build();
    }

    public boolean isOwnedBy(Long candidateCustomerId) {
        return candidateCustomerId != null && Objects.equals(this.customerId, candidateCustomerId);
    }

    /**
     * CMS-17 AC02: Records the staff-approved submission as delivered preview, starts the
     * auto-complete countdown and transitions the commission to PREVIEW_DELIVERED.
     */
    public void deliverPreview(Long submissionId, Instant autoCompleteAt) {
        this.approvedSubmissionId = submissionId;
        this.autoCompleteAt = autoCompleteAt;
        this.status = CommissionStatus.PREVIEW_DELIVERED;
    }

    public String resolveActivePaymentCode() {
        if (this.status != null && this.status.isPaymentPending()) {
            return this.activePaymentCode;
        }
        return null;
    }
}
