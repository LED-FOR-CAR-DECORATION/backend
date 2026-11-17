package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Full internal management details of a commission ticket, including customer profile, full draft submission history, assignment metadata, and SLA timestamps")
@Builder
public record ManagementCommissionDetailResponse(
        @Schema(description = "Unique commission request ID", example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Customer ID who requested the commission", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        Long customerId,

        @Schema(description = "Customer full name", example = "Nguyen Van A", nullable = true)
        String customerName,

        @Schema(description = "Customer email address", example = "nguyenvana@gmail.com", nullable = true)
        String customerEmail,

        @Schema(description = "Customer phone number", example = "0901234567", nullable = true)
        String customerPhone,

        @Schema(description = "Commission request title", example = "Cyberpunk Neon Cat Animation", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "Detailed customer artwork description", example = "Animated neon cat blinking", nullable = true)
        String description,

        @Schema(description = "Selected pricing tier code", example = "ADVANCED", requiredMode = Schema.RequiredMode.REQUIRED)
        String tierCode,

        @Schema(description = "Target LED matrix resolution", example = "32x64", nullable = true)
        String targetMatrixDimensions,

        @Schema(description = "Immutable snapshot JSON of pricing configuration at creation", example = "{\"tierCode\":\"ADVANCED\"}", nullable = true)
        String priceBreakdownJson,

        @Schema(description = "Initial package/configuration price in VND", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal initialPrice,

        @Schema(description = "Accumulated extra revision fee in VND", example = "0.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal extraRevisionFee,

        @Schema(description = "Total commission price in VND (initialPrice + extraRevisionFee)", example = "250000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal totalPrice,

        @Schema(description = "Number of free revisions consumed", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int revisionsUsed,

        @Schema(description = "Maximum free revisions included in the commission", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int maxRevisions,

        @Schema(description = "Current commission lifecycle status across 13 states", example = "INTERNAL_REVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus status,

        @Schema(description = "Assigned staff employee ID", example = "3", nullable = true)
        Long assignedStaffId,

        @Schema(description = "Assigned staff employee full name", example = "Tran Thi Staff", nullable = true)
        String assignedStaffName,

        @Schema(description = "Assigned graphic designer employee ID", example = "5", nullable = true)
        Long assignedDesignerId,

        @Schema(description = "Assigned graphic designer employee full name", example = "Le Van Designer", nullable = true)
        String assignedDesignerName,

        @Schema(description = "Whether the commission processing has exceeded SLA timeline", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isSlaOverdue,

        @Schema(description = "Active SePay payment code when payment is pending", example = "LMT-CMS-105", nullable = true)
        String activePaymentCode,

        @Schema(description = "Final approved submission ID", example = "902", nullable = true)
        Long approvedSubmissionId,

        @Schema(description = "Reason provided if the commission request was rejected", example = "Dimensions unsupported", nullable = true)
        String rejectionReason,

        @Schema(description = "Whether the commission chat channel is currently active", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean chatActive,

        @Schema(description = "Quotation payment expiration timestamp in UTC", example = "2026-11-19T10:00:00Z", nullable = true)
        Instant quoteExpiresAt,

        @Schema(description = "SLA due timestamp in UTC", example = "2026-11-18T10:00:00Z", nullable = true)
        Instant slaDueAt,

        @Schema(description = "Auto-completion deadline timestamp in UTC", example = "2026-11-22T10:00:00Z", nullable = true)
        Instant autoCompleteAt,

        @Schema(description = "Timestamp when the commission request was submitted in UTC", example = "2026-11-17T08:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(description = "Timestamp when the commission request was last updated in UTC", example = "2026-11-17T08:20:00Z", nullable = true)
        Instant updatedAt,

        @Schema(description = "List of reference attachments uploaded by customer", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ManagementCommissionAttachmentItem> attachments,

        @Schema(description = "Full internal draft submission history including pending review and rejected drafts", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ManagementCommissionSubmissionItem> submissions
) {
    @Schema(description = "Customer reference image attachment")
    @Builder
    public record ManagementCommissionAttachmentItem(
            @Schema(description = "Attachment identifier", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
            Long attachmentId,

            @Schema(description = "URL of reference image", example = "https://cdn.lumintra.shop/commissions/ref-cat.png", requiredMode = Schema.RequiredMode.REQUIRED)
            String imageUrl,

            @Schema(description = "Caption or note describing reference image", example = "Cat ears reference", nullable = true)
            String caption,

            @Schema(description = "Display order index", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
            int displayOrder,

            @Schema(description = "Timestamp when attachment was uploaded", example = "2026-11-17T08:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt
    ) {}

    @Schema(description = "Internal graphic submission version draft")
    @Builder
    public record ManagementCommissionSubmissionItem(
            @Schema(description = "Submission identifier", example = "901", requiredMode = Schema.RequiredMode.REQUIRED)
            Long submissionId,

            @Schema(description = "Author designer employee ID", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
            Long designerId,

            @Schema(description = "Author designer employee full name", example = "Le Van Designer", nullable = true)
            String designerName,

            @Schema(description = "Incremental version sequence number", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            int versionNumber,

            @Schema(description = "URL of drafted graphic asset", example = "https://cdn.lumintra.shop/commissions/draft-v1.gif", requiredMode = Schema.RequiredMode.REQUIRED)
            String imageUrl,

            @Schema(description = "Notes attached by designer", example = "Initial neon purple background", nullable = true)
            String designerNote,

            @Schema(description = "Review status of this draft submission", example = "STAFF_REJECTED", requiredMode = Schema.RequiredMode.REQUIRED)
            CommissionSubmissionStatus status,

            @Schema(description = "Reviewer staff employee ID", example = "3", nullable = true)
            Long reviewerStaffId,

            @Schema(description = "Reviewer staff employee full name", example = "Tran Thi Staff", nullable = true)
            String reviewerStaffName,

            @Schema(description = "Internal review feedback notes by staff", example = "Blinking too fast", nullable = true)
            String staffReviewNote,

            @Schema(description = "Timestamp when staff reviewed this draft", example = "2026-11-17T08:15:00Z", nullable = true)
            Instant reviewedAt,

            @Schema(description = "Timestamp when draft was delivered to customer", example = "2026-11-17T08:30:00Z", nullable = true)
            Instant deliveredAt,

            @Schema(description = "Timestamp when draft was submitted by designer", example = "2026-11-17T08:10:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt
    ) {}
}
