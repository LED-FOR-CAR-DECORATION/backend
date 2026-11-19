package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ApproveDeliverableCardResponse;
import com.lumintra.backend.commission.application.service.CommissionCompletionService;
import com.lumintra.backend.commission.domain.model.CommissionCompletionSource;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * CMS-18: Customer approves the Deliverable Card, finalizing the commission (BR-FL3-06).
 * The completion itself is shared with the CMS-27 auto-approve job ({@link CommissionCompletionService}).
 */
@Service
@RequiredArgsConstructor
public class ApproveDeliverableCardUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveDeliverableCardUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionCompletionService completionService;

    @Transactional
    public ApproveDeliverableCardResponse execute(Long commissionId, Long customerId) {
        log.info("Processing deliverable card approval [commissionId={}] [customerId={}]", commissionId, customerId);

        // Row lock serializes concurrent approvals (and the CMS-27 job) so CommissionCompletedEvent is published at most once
        CommissionRequestEntity commission = commissionRequestRepository.findByIdForUpdate(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        // AC01: Restrict access to the owning CUSTOMER
        if (!commission.isOwnedBy(customerId)) {
            log.warn("Commission ownership mismatch during deliverable card approval [commissionId={}] [customerId={}]",
                    commissionId, customerId);
            throw new CommissionOwnershipMismatchException("You do not have permission to access this commission request.");
        }

        // AC02: Only PREVIEW_DELIVERED can transition to COMPLETED
        if (commission.getStatus() != CommissionStatus.PREVIEW_DELIVERED) {
            log.warn("Invalid commission status for deliverable card approval [commissionId={}] [currentStatus={}]",
                    commissionId, commission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot approve deliverable card for commission in status " + commission.getStatus()
                            + " (must be PREVIEW_DELIVERED)"
            );
        }

        // AC02: Mark card ACCEPTED, set approved_submission_id, PREVIEW_DELIVERED -> COMPLETED, publish CommissionCompletedEvent
        CommissionCompletionService.Completion completion =
                completionService.complete(commission, CommissionCompletionSource.CUSTOMER_APPROVED, Instant.now());

        log.info("Deliverable card approved, commission completed [commissionId={}] [submissionId={}]",
                commissionId, completion.submission().getSubmissionId());

        return ApproveDeliverableCardResponse.builder()
                .commissionId(completion.commission().getCommissionId())
                .commissionStatus(completion.commission().getStatus())
                .approvedSubmissionId(completion.submission().getSubmissionId())
                .versionNumber(completion.submission().getVersionNumber())
                .submissionStatus(completion.submission().getStatus())
                .completedAt(completion.completedAt())
                .systemMessage(completion.systemMessage())
                .build();
    }
}
