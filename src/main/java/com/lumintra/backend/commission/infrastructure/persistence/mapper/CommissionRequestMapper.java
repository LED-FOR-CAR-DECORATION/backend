package com.lumintra.backend.commission.infrastructure.persistence.mapper;

import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse.CustomerCommissionAttachmentItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse.CustomerDeliveredPreviewItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse.StaffCommissionQueueItem;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.shared.response.PaginationMeta;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps persisted {@link CommissionRequestEntity} records and child collections into customer and management response DTOs.
 */
@Component
public class CommissionRequestMapper {

    public CustomerCommissionListResponse toCustomerListResponse(Page<CommissionRequestEntity> page) {
        if (page == null) {
            return CustomerCommissionListResponse.builder()
                    .items(List.of())
                    .meta(null)
                    .build();
        }

        List<CustomerCommissionSummaryItem> items = page.getContent().stream()
                .map(this::toCustomerSummaryItem)
                .toList();

        return CustomerCommissionListResponse.builder()
                .items(items)
                .meta(PaginationMeta.from(page))
                .build();
    }

    public CustomerCommissionSummaryItem toCustomerSummaryItem(CommissionRequestEntity entity) {
        if (entity == null) {
            return null;
        }

        return CustomerCommissionSummaryItem.builder()
                .commissionId(entity.getCommissionId())
                .title(entity.getTitle())
                .tierCode(entity.getTierCode())
                .targetMatrixDimensions(entity.getTargetMatrixDimensions())
                .status(entity.getStatus())
                .initialPrice(entity.getInitialPrice())
                .extraRevisionFee(entity.getExtraRevisionFee())
                .totalPrice(entity.getTotalPrice())
                .revisionsUsed(entity.getRevisionsUsed())
                .maxRevisions(entity.getMaxRevisions())
                .activePaymentCode(entity.resolveActivePaymentCode())
                .quoteExpiresAt(entity.getQuoteExpiresAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public StaffCommissionQueueResponse toStaffQueueResponse(
            Page<CommissionRequestEntity> page,
            Map<Long, CustomerProfileSnapshot> customerProfiles,
            Map<Long, EmployeeProfileSnapshot> employeeProfiles) {
        if (page == null) {
            return StaffCommissionQueueResponse.builder()
                    .items(List.of())
                    .meta(null)
                    .build();
        }

        List<StaffCommissionQueueItem> items = page.getContent().stream()
                .map(entity -> toStaffQueueItem(entity, customerProfiles, employeeProfiles))
                .toList();

        return StaffCommissionQueueResponse.builder()
                .items(items)
                .meta(PaginationMeta.from(page))
                .build();
    }

    public StaffCommissionQueueItem toStaffQueueItem(
            CommissionRequestEntity entity,
            Map<Long, CustomerProfileSnapshot> customerProfiles,
            Map<Long, EmployeeProfileSnapshot> employeeProfiles) {
        if (entity == null) {
            return null;
        }

        CustomerProfileSnapshot customer = entity.getCustomerId() != null && customerProfiles != null
                ? customerProfiles.get(entity.getCustomerId())
                : null;

        EmployeeProfileSnapshot staff = entity.getAssignedStaffId() != null && employeeProfiles != null
                ? employeeProfiles.get(entity.getAssignedStaffId())
                : null;

        EmployeeProfileSnapshot designer = entity.getAssignedDesignerId() != null && employeeProfiles != null
                ? employeeProfiles.get(entity.getAssignedDesignerId())
                : null;

        return StaffCommissionQueueItem.builder()
                .commissionId(entity.getCommissionId())
                .customerId(entity.getCustomerId())
                .customerName(customer != null ? customer.fullName() : null)
                .customerEmail(customer != null ? customer.email() : null)
                .title(entity.getTitle())
                .tierCode(entity.getTierCode())
                .targetMatrixDimensions(entity.getTargetMatrixDimensions())
                .status(entity.getStatus())
                .initialPrice(entity.getInitialPrice())
                .extraRevisionFee(entity.getExtraRevisionFee())
                .totalPrice(entity.getTotalPrice())
                .revisionsUsed(entity.getRevisionsUsed())
                .maxRevisions(entity.getMaxRevisions())
                .assignedStaffId(entity.getAssignedStaffId())
                .assignedStaffName(staff != null ? staff.fullName() : null)
                .assignedDesignerId(entity.getAssignedDesignerId())
                .assignedDesignerName(designer != null ? designer.fullName() : null)
                .isSlaOverdue(entity.isSlaOverdue())
                .quoteExpiresAt(entity.getQuoteExpiresAt())
                .slaDueAt(entity.getSlaDueAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public CustomerCommissionDetailResponse toCustomerDetailResponse(
            CommissionRequestEntity entity,
            List<CommissionAttachmentEntity> attachments,
            List<CommissionSubmissionEntity> submissions) {
        if (entity == null) {
            return null;
        }

        List<CustomerCommissionAttachmentItem> attachmentItems = attachments == null
                ? List.of()
                : attachments.stream()
                        .filter(Objects::nonNull)
                        .map(this::toCustomerAttachmentItem)
                        .toList();

        List<CustomerDeliveredPreviewItem> previewItems = submissions == null
                ? List.of()
                : submissions.stream()
                        .filter(Objects::nonNull)
                        .filter(CommissionSubmissionEntity::isCustomerVisible)
                        .map(this::toCustomerDeliveredPreviewItem)
                        .toList();

        return CustomerCommissionDetailResponse.builder()
                .commissionId(entity.getCommissionId())
                .customerId(entity.getCustomerId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .tierCode(entity.getTierCode())
                .targetMatrixDimensions(entity.getTargetMatrixDimensions())
                .priceBreakdownJson(entity.getPriceBreakdownJson())
                .initialPrice(entity.getInitialPrice())
                .extraRevisionFee(entity.getExtraRevisionFee())
                .totalPrice(entity.getTotalPrice())
                .revisionsUsed(entity.getRevisionsUsed())
                .maxRevisions(entity.getMaxRevisions())
                .status(entity.getStatus())
                .activePaymentCode(entity.resolveActivePaymentCode())
                .approvedSubmissionId(entity.getApprovedSubmissionId())
                .rejectionReason(entity.getRejectionReason())
                .chatActive(entity.getStatus() != null && entity.getStatus().isChatActive())
                .quoteExpiresAt(entity.getQuoteExpiresAt())
                .slaDueAt(entity.getSlaDueAt())
                .autoCompleteAt(entity.getAutoCompleteAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .attachments(attachmentItems)
                .deliveredPreviews(previewItems)
                .build();
    }

    public CustomerCommissionAttachmentItem toCustomerAttachmentItem(CommissionAttachmentEntity entity) {
        if (entity == null) {
            return null;
        }

        return CustomerCommissionAttachmentItem.builder()
                .attachmentId(entity.getAttachmentId())
                .imageUrl(entity.getImageUrl())
                .caption(entity.getCaption())
                .displayOrder(entity.getDisplayOrder())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public CustomerDeliveredPreviewItem toCustomerDeliveredPreviewItem(CommissionSubmissionEntity entity) {
        if (entity == null || !entity.isCustomerVisible()) {
            return null;
        }

        return CustomerDeliveredPreviewItem.builder()
                .submissionId(entity.getSubmissionId())
                .versionNumber(entity.getVersionNumber())
                .imageUrl(entity.getImageUrl())
                .designerNote(entity.getDesignerNote())
                .status(entity.getStatus())
                .deliveredAt(entity.getDeliveredAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
