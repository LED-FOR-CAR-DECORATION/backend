package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Detailed customer view of a personal custom artwork commission request, including requirements, payment code, reference attachments, and Staff-approved delivered previews")
@Builder
public record CustomerCommissionDetailResponse(
        @Schema(description = "Unique commission request identifier", example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
        Long commissionId,

        @Schema(description = "Owning customer account identifier", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long customerId,

        @Schema(description = "Customer-supplied commission title", example = "Cyberpunk Shiba Inu Pixel Art", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "Detailed customer character description and artwork requirements", example = "Animated neon Shiba Inu winking with rain background for 64x64 matrix", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String description,

        @Schema(description = "Selected pricing tier code (`BASIC`, `ADVANCED`, `CUSTOM`)", example = "ADVANCED", requiredMode = Schema.RequiredMode.REQUIRED)
        String tierCode,

        @Schema(description = "Target LED matrix pixel dimensions", example = "64x64", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String targetMatrixDimensions,

        @Schema(description = "Immutable JSON snapshot of selected pricing rules and option prices at submission time", example = "{\"tierCode\":\"ADVANCED\",\"basePrice\":350000.00}", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String priceBreakdownJson,

        @Schema(description = "Snapshotted initial commission price in VND", example = "350000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal initialPrice,

        @Schema(description = "Accumulated extra paid revision fees in VND", example = "35000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal extraRevisionFee,

        @Schema(description = "Total commission price (`initialPrice + extraRevisionFee`) in VND", example = "385000.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal totalPrice,

        @Schema(description = "Number of customer revisions consumed so far", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        int revisionsUsed,

        @Schema(description = "Free revision quota included in the selected tier", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        int maxRevisions,

        @Schema(description = "Current commission lifecycle status", example = "PREVIEW_DELIVERED", requiredMode = Schema.RequiredMode.REQUIRED)
        CommissionStatus status,

        @Schema(description = "Active SePay payment code exposed only when status is `PENDING_PAYMENT` or `AWAITING_EXTRA_PAYMENT` (`null` otherwise)", example = "LMTCMS501A1B2", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String activePaymentCode,

        @Schema(description = "Submission ID accepted by the customer upon completion (`null` until `COMPLETED`)", example = "9002", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long approvedSubmissionId,

        @Schema(description = "Reason provided by Staff if the initial request was rejected (`null` unless rejected)", example = "Requested dimensions exceed supported matrix resolution", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String rejectionReason,

        @Schema(description = "Whether the customer-facing commission chat room (`/api/commissions/{id}/messages`) is currently active", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean chatActive,

        @Schema(description = "ISO-8601 UTC expiration timestamp for initial quote payment (`PENDING_PAYMENT`)", example = "2026-11-17T09:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant quoteExpiresAt,

        @Schema(description = "ISO-8601 UTC target delivery deadline for the current draft or revision", example = "2026-11-19T09:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant slaDueAt,

        @Schema(description = "ISO-8601 UTC timestamp when a delivered preview will auto-complete if no customer action is taken", example = "2026-11-23T09:00:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant autoCompleteAt,

        @Schema(description = "ISO-8601 UTC timestamp when the commission request was created", example = "2026-11-15T10:15:30Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(description = "ISO-8601 UTC timestamp when the commission request was last updated", example = "2026-11-16T08:20:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant updatedAt,

        @Schema(description = "Customer reference image attachments submitted with the request", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CustomerCommissionAttachmentItem> attachments,

        @Schema(description = "Staff-approved artwork previews delivered to the customer (excludes unapproved internal Designer drafts)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<CustomerDeliveredPreviewItem> deliveredPreviews
) {

    @Schema(description = "Reference attachment uploaded by the customer for the commission request")
    @Builder
    public record CustomerCommissionAttachmentItem(
            @Schema(description = "Attachment identifier", example = "701", requiredMode = Schema.RequiredMode.REQUIRED)
            Long attachmentId,

            @Schema(description = "Cloud storage URL of the reference image", example = "https://storage.googleapis.com/lumintra-buckets/commission-references/ref-1.png", requiredMode = Schema.RequiredMode.REQUIRED)
            String imageUrl,

            @Schema(description = "Optional caption or note describing the reference image", example = "Front angle neon visor palette", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String caption,

            @Schema(description = "Zero-based display order of the attachment", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
            int displayOrder,

            @Schema(description = "ISO-8601 UTC timestamp when the attachment was recorded", example = "2026-11-15T10:15:30Z", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt
    ) {
    }

    @Schema(description = "Customer-visible artwork preview approved by Staff QA")
    @Builder
    public record CustomerDeliveredPreviewItem(
            @Schema(description = "Submission identifier", example = "9002", requiredMode = Schema.RequiredMode.REQUIRED)
            Long submissionId,

            @Schema(description = "Sequential draft version number", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            int versionNumber,

            @Schema(description = "Cloud storage URL of the delivered preview artwork (PNG/GIF)", example = "https://storage.googleapis.com/lumintra-buckets/commission-drafts/shiba-v2.gif", requiredMode = Schema.RequiredMode.REQUIRED)
            String imageUrl,

            @Schema(description = "Optional note accompanying the delivered preview", example = "Adjusted visor glow and 24-frame rain loop", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String designerNote,

            @Schema(description = "Customer-visible submission status (`STAFF_APPROVED` or `CUSTOMER_ACCEPTED`)", example = "STAFF_APPROVED", requiredMode = Schema.RequiredMode.REQUIRED)
            CommissionSubmissionStatus status,

            @Schema(description = "ISO-8601 UTC timestamp when Staff delivered this preview to the customer", example = "2026-11-16T08:20:00Z", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Instant deliveredAt,

            @Schema(description = "ISO-8601 UTC timestamp when the draft version was uploaded", example = "2026-11-16T07:45:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt
    ) {
    }
}
