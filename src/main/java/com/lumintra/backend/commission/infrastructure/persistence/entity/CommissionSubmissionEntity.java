package com.lumintra.backend.commission.infrastructure.persistence.entity;

import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
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

import java.time.Instant;

@Entity
@Table(
        name = "commission_submission",
        indexes = {
                @Index(name = "idx_commission_submission_commission_version", columnList = "commission_id, version_number"),
                @Index(name = "idx_commission_submission_commission_status", columnList = "commission_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommissionSubmissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "commission_id", nullable = false)
    private Long commissionId;

    @Column(name = "designer_id", nullable = false)
    private Long designerId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "designer_note", columnDefinition = "TEXT")
    private String designerNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private CommissionSubmissionStatus status = CommissionSubmissionStatus.PENDING_STAFF_REVIEW;

    @Column(name = "reviewer_staff_id")
    private Long reviewerStaffId;

    @Column(name = "staff_review_note", columnDefinition = "TEXT")
    private String staffReviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CommissionSubmissionEntity createDraft(
            Long commissionId,
            Long designerId,
            int versionNumber,
            String imageUrl,
            String designerNote) {
        return CommissionSubmissionEntity.builder()
                .commissionId(commissionId)
                .designerId(designerId)
                .versionNumber(Math.max(1, versionNumber))
                .imageUrl(imageUrl != null ? imageUrl.trim() : "")
                .designerNote(designerNote != null && !designerNote.isBlank() ? designerNote.trim() : null)
                .status(CommissionSubmissionStatus.PENDING_STAFF_REVIEW)
                .build();
    }

    public boolean isPendingStaffReview() {
        return this.status == CommissionSubmissionStatus.PENDING_STAFF_REVIEW;
    }

    /**
     * CMS-17 AC02: Staff approves this draft for customer delivery.
     */
    public void approveByStaff(Long staffId, Instant approvedAt) {
        this.status = CommissionSubmissionStatus.STAFF_APPROVED;
        this.reviewerStaffId = staffId;
        this.reviewedAt = approvedAt;
        this.deliveredAt = approvedAt;
    }

    public boolean isCustomerVisible() {
        return this.status != null && this.status.isCustomerVisible();
    }
}
