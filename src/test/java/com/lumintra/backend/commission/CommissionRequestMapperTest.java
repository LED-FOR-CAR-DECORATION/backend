package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.api.response.ManagementCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse.StaffCommissionQueueItem;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Test
    @DisplayName("CMS-06 Mapper UTCID05 [N]: Maps staff commission queue items with enriched customer and employee names")
    void toStaffQueueResponse_mapsEnrichedProfilesAndItemsCorrectly() {
        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                10L, "Cyber Neon Car", "Detailed description", "ADVANCED", "32x64", "{}", new BigDecimal("250000.00"), 2, 2L
        );
        entity.setCommissionId(101L);
        entity.setAssignedDesignerId(5L);
        entity.setSlaDueAt(Instant.parse("2026-11-20T10:00:00Z"));

        Map<Long, CustomerProfileSnapshot> customers = Map.of(
                10L, new CustomerProfileSnapshot(10L, "Nguyen Van A", "0901234567", "nguyenvana@gmail.com")
        );
        Map<Long, EmployeeProfileSnapshot> employees = Map.of(
                2L, new EmployeeProfileSnapshot(2L, "Staff Hoang", "staff@lumintra.com"),
                5L, new EmployeeProfileSnapshot(5L, "Designer Nam", "designer@lumintra.com")
        );

        StaffCommissionQueueResponse response = mapper.toStaffQueueResponse(
                new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1),
                customers,
                employees
        );

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        StaffCommissionQueueItem item = response.items().get(0);
        assertThat(item.commissionId()).isEqualTo(101L);
        assertThat(item.customerName()).isEqualTo("Nguyen Van A");
        assertThat(item.customerEmail()).isEqualTo("nguyenvana@gmail.com");
        assertThat(item.assignedStaffName()).isEqualTo("Staff Hoang");
        assertThat(item.assignedDesignerName()).isEqualTo("Designer Nam");
        assertThat(item.status()).isEqualTo(CommissionStatus.PENDING_REVIEW);
        assertThat(item.targetMatrixDimensions()).isEqualTo("32x64");
    }

    @Test
    @DisplayName("CMS-06 Mapper UTCID06 [B]: Handles null page and null inputs safely for staff queue")
    void toStaffQueueResponse_handlesNullInputsSafely() {
        StaffCommissionQueueResponse response = mapper.toStaffQueueResponse(null, null, null);
        assertThat(response).isNotNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.meta()).isNull();

        assertThat(mapper.toStaffQueueItem(null, null, null)).isNull();
    }

    @Test
    @DisplayName("CMS-07 Mapper UTCID07 [N]: Maps full management detail including unapproved draft submissions and employee profiles")
    void toManagementDetailResponse_mapsFullDetailsAndUnapprovedSubmissions() {
        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                10L, "Cyber Neon Car", "Detailed description", "ADVANCED", "32x64", "{}", new BigDecimal("250000.00"), 2, 2L
        );
        entity.setCommissionId(101L);
        entity.setAssignedDesignerId(5L);
        entity.setStatus(CommissionStatus.INTERNAL_REVIEW);

        CommissionAttachmentEntity attachment = CommissionAttachmentEntity.create(
                101L, "https://cdn.lumintra.shop/ref.png", "Ref", 0
        );
        CommissionSubmissionEntity draft = CommissionSubmissionEntity.createDraft(
                101L, 5L, 1, "https://cdn.lumintra.shop/draft.gif", "Draft"
        );
        draft.setStatus(CommissionSubmissionStatus.PENDING_STAFF_REVIEW);

        CustomerProfileSnapshot customer = new CustomerProfileSnapshot(10L, "Nguyen Van A", "0901234567", "nguyenvana@gmail.com");
        Map<Long, EmployeeProfileSnapshot> employees = Map.of(
                2L, new EmployeeProfileSnapshot(2L, "Staff Hoang", "staff@lumintra.com"),
                5L, new EmployeeProfileSnapshot(5L, "Designer Nam", "designer@lumintra.com")
        );

        ManagementCommissionDetailResponse detail = mapper.toManagementDetailResponse(
                entity, List.of(attachment), List.of(draft), customer, employees
        );

        assertThat(detail).isNotNull();
        assertThat(detail.commissionId()).isEqualTo(101L);
        assertThat(detail.customerName()).isEqualTo("Nguyen Van A");
        assertThat(detail.customerPhone()).isEqualTo("0901234567");
        assertThat(detail.assignedStaffName()).isEqualTo("Staff Hoang");
        assertThat(detail.assignedDesignerName()).isEqualTo("Designer Nam");
        assertThat(detail.chatActive()).isTrue();
        assertThat(detail.attachments()).hasSize(1);
        assertThat(detail.submissions()).hasSize(1);
        assertThat(detail.submissions().get(0).designerName()).isEqualTo("Designer Nam");
        assertThat(detail.submissions().get(0).status()).isEqualTo(CommissionSubmissionStatus.PENDING_STAFF_REVIEW);
    }

    @Test
    @DisplayName("CMS-07 Mapper UTCID08 [B]: Handles null entity, null lists and null inputs safely in management detail mapping")
    void toManagementDetailResponse_handlesNullInputsSafely() {
        assertThat(mapper.toManagementDetailResponse(null, null, null, null, null)).isNull();
        assertThat(mapper.toManagementAttachmentItem(null)).isNull();
        assertThat(mapper.toManagementSubmissionItem(null, null)).isNull();

        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                10L, "Simple Star", null, "BASIC", "32x32", null, new BigDecimal("150000.00"), 1, 20L
        );
        ManagementCommissionDetailResponse detail = mapper.toManagementDetailResponse(entity, null, null, null, null);
        assertThat(detail.attachments()).isEmpty();
        assertThat(detail.submissions()).isEmpty();
        assertThat(detail.customerName()).isNull();
    }
}
