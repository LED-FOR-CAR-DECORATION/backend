package com.lumintra.backend.commission.infrastructure.persistence.mapper;

import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.shared.response.PaginationMeta;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps persisted {@link CommissionRequestEntity} records into customer and management response DTOs.
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
}
