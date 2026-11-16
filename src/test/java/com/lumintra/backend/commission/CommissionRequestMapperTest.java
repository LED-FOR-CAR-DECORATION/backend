package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionRequestMapperTest {

    private final CommissionRequestMapper mapper = new CommissionRequestMapper();

    @Test
    @DisplayName("CMS-04 Mapper UTCID01 [N]: Exposes activePaymentCode only when status is payment-pending")
    void toCustomerSummaryItem_exposesActivePaymentCodeOnlyWhenPaymentPending() {
        CommissionRequestEntity pendingPayment = CommissionRequestEntity.createPendingReview(
                10L, "Custom Cat", "Neon cat", "BASIC", "32x32", "{}", new BigDecimal("79000.00"), 1, 20L
        );
        pendingPayment.setCommissionId(100L);
        pendingPayment.setStatus(CommissionStatus.PENDING_PAYMENT);
        pendingPayment.setActivePaymentCode("LMT-CMS-100");

        CustomerCommissionSummaryItem pendingItem = mapper.toCustomerSummaryItem(pendingPayment);
        assertThat(pendingItem.activePaymentCode()).isEqualTo("LMT-CMS-100");
        assertThat(pendingPayment.isOwnedBy(10L)).isTrue();
        assertThat(pendingPayment.isOwnedBy(99L)).isFalse();

        pendingPayment.setStatus(CommissionStatus.PAID);
        CustomerCommissionSummaryItem paidItem = mapper.toCustomerSummaryItem(pendingPayment);
        assertThat(paidItem.activePaymentCode()).isNull();
        assertThat(CommissionStatus.PAID.isChatActive()).isTrue();
        assertThat(CommissionStatus.PENDING_REVIEW.isChatActive()).isFalse();
    }

    @Test
    @DisplayName("CMS-04 Mapper UTCID02 [B]: Handles null entity and null page safely")
    void toCustomerListResponse_handlesNullInputs() {
        assertThat(mapper.toCustomerSummaryItem(null)).isNull();

        CustomerCommissionListResponse response = mapper.toCustomerListResponse(null);
        assertThat(response.items()).isEmpty();
        assertThat(response.meta()).isNull();
    }

    @Test
    @DisplayName("CMS-05 Mapper UTCID03 [N]: Maps customer detail and filters out unapproved internal Designer drafts (AC03)")
    void toCustomerDetailResponse_filtersOutUnapprovedInternalDrafts() {
        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                10L, "Mecha Fox", "Animated 64x64", "ADVANCED", "64x64", "{\"tier\":\"ADVANCED\"}",
                new BigDecimal("350000.00"), 3, 20L
        );
        entity.setCommissionId(501L);
        entity.setStatus(CommissionStatus.AWAITING_EXTRA_PAYMENT);
        entity.setActivePaymentCode("LMTCMS501EXTRA");

        CommissionAttachmentEntity attachment = CommissionAttachmentEntity.create(
                501L, "https://storage.googleapis.com/lumintra-buckets/commission-references/fox.png", "Pose ref", 0
        );
        attachment.setAttachmentId(701L);

        CommissionSubmissionEntity pendingDraft = CommissionSubmissionEntity.createDraft(
                501L, 30L, 1, "https://storage.googleapis.com/lumintra-buckets/commission-drafts/v1.gif", "Internal WIP"
        );
        pendingDraft.setSubmissionId(9001L);

        CommissionSubmissionEntity rejectedDraft = CommissionSubmissionEntity.createDraft(
                501L, 30L, 2, "https://storage.googleapis.com/lumintra-buckets/commission-drafts/v2.gif", "Wrong palette"
        );
        rejectedDraft.setSubmissionId(9002L);
        rejectedDraft.setStatus(CommissionSubmissionStatus.STAFF_REJECTED);

        CommissionSubmissionEntity approvedDraft = CommissionSubmissionEntity.createDraft(
                501L, 30L, 3, "https://storage.googleapis.com/lumintra-buckets/commission-drafts/v3.gif", "Ready for customer"
        );
        approvedDraft.setSubmissionId(9003L);
        approvedDraft.setStatus(CommissionSubmissionStatus.STAFF_APPROVED);
        approvedDraft.setDeliveredAt(Instant.parse("2026-11-16T08:00:00Z"));

        CommissionSubmissionEntity acceptedDraft = CommissionSubmissionEntity.createDraft(
                501L, 30L, 4, "https://storage.googleapis.com/lumintra-buckets/commission-drafts/v4.gif", "Final accepted"
        );
        acceptedDraft.setSubmissionId(9004L);
        acceptedDraft.setStatus(CommissionSubmissionStatus.CUSTOMER_ACCEPTED);

        CustomerCommissionDetailResponse detail = mapper.toCustomerDetailResponse(
                entity,
                List.of(attachment),
                List.of(pendingDraft, rejectedDraft, approvedDraft, acceptedDraft)
        );

        assertThat(detail.commissionId()).isEqualTo(501L);
        assertThat(detail.activePaymentCode()).isEqualTo("LMTCMS501EXTRA");
        assertThat(detail.chatActive()).isTrue();
        assertThat(detail.attachments()).hasSize(1);
        assertThat(detail.deliveredPreviews()).hasSize(2);
        assertThat(detail.deliveredPreviews())
                .extracting(CustomerCommissionDetailResponse.CustomerDeliveredPreviewItem::submissionId)
                .containsExactly(9003L, 9004L);
        assertThat(mapper.toCustomerDeliveredPreviewItem(pendingDraft)).isNull();
        assertThat(mapper.toCustomerDeliveredPreviewItem(rejectedDraft)).isNull();
    }

    @Test
    @DisplayName("CMS-05 Mapper UTCID04 [B]: Handles null entity and null child collections in detail mapping")
    void toCustomerDetailResponse_handlesNullInputsSafely() {
        assertThat(mapper.toCustomerDetailResponse(null, List.of(), List.of())).isNull();
        assertThat(mapper.toCustomerAttachmentItem(null)).isNull();
        assertThat(mapper.toCustomerDeliveredPreviewItem(null)).isNull();

        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                10L, "Simple Star", null, "BASIC", "32x32", null, new BigDecimal("150000.00"), 1, 20L
        );
        CustomerCommissionDetailResponse detail = mapper.toCustomerDetailResponse(entity, null, null);
        assertThat(detail.attachments()).isEmpty();
        assertThat(detail.deliveredPreviews()).isEmpty();
    }
}
