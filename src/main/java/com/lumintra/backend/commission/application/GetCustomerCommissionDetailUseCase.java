package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionAttachmentRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case orchestrator for retrieving the customer-facing detail view of a personal commission request ({@code CMS-05}).
 *
 * <p>Enforces customer ownership ({@code AC01}), resolves {@code activePaymentCode} when payment is pending ({@code AC02}),
 * and strictly filters submissions to Staff-approved previews so unapproved internal Designer drafts are never exposed ({@code AC03}).</p>
 */
@Service
@RequiredArgsConstructor
public class GetCustomerCommissionDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetCustomerCommissionDetailUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionAttachmentRepository commissionAttachmentRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final CommissionRequestMapper commissionRequestMapper;

    @Transactional(readOnly = true)
    public CustomerCommissionDetailResponse execute(Long customerId, Long commissionId) {
        if (customerId == null || customerId <= 0) {
            throw new InvalidRuleConfigurationException("Authenticated customer ID is required.");
        }
        if (commissionId == null || commissionId <= 0) {
            throw new CommissionNotFoundException("Commission request not found.");
        }

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> {
                    log.warn("Commission request not found [commissionId={}] [customerId={}]", commissionId, customerId);
                    return new CommissionNotFoundException("Commission request not found with id: " + commissionId);
                });

        if (!commission.isOwnedBy(customerId)) {
            log.warn("Commission ownership mismatch [commissionId={}] [customerId={}] [ownerCustomerId={}]",
                    commissionId, customerId, commission.getCustomerId());
            throw new CommissionOwnershipMismatchException("You do not have permission to access this commission request.");
        }

        List<CommissionAttachmentEntity> attachments = commissionAttachmentRepository
                .findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(commissionId);

        List<CommissionSubmissionEntity> approvedPreviews = commissionSubmissionRepository
                .findAllByCommissionIdAndStatusInOrderByVersionNumberAscSubmissionIdAsc(
                        commissionId,
                        CommissionSubmissionStatus.customerVisibleStatuses()
                );

        log.info("Retrieved customer commission detail [commissionId={}] [customerId={}] [status={}] [attachments={}] [deliveredPreviews={}]",
                commissionId, customerId, commission.getStatus(), attachments.size(), approvedPreviews.size());

        return commissionRequestMapper.toCustomerDetailResponse(commission, attachments, approvedPreviews);
    }
}
