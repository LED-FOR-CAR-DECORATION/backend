package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ApproveDeliverableCardResponse;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.domain.event.CommissionCompletedEvent;
import com.lumintra.backend.commission.domain.model.CommissionCompletionSource;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * CMS-18: Customer approves the Deliverable Card, finalizing the commission (BR-FL3-06).
 */
@Service
@RequiredArgsConstructor
public class ApproveDeliverableCardUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveDeliverableCardUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final CommissionMessageRepository commissionMessageRepository;
    private final CommissionMessageMapper commissionMessageMapper;
    private final CommissionChatBroadcastPort chatBroadcastPort;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ApproveDeliverableCardResponse execute(Long commissionId, Long customerId) {
        log.info("Processing deliverable card approval [commissionId={}] [customerId={}]", commissionId, customerId);

        // Row lock serializes concurrent approvals so CommissionCompletedEvent is published at most once
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

        CommissionSubmissionEntity submission = resolveDeliveredSubmission(commission)
                .orElseThrow(() -> {
                    log.warn("No delivered preview found for deliverable card approval [commissionId={}] [approvedSubmissionId={}]",
                            commissionId, commission.getApprovedSubmissionId());
                    return new InvalidCommissionStatusTransitionException(
                            "Commission request " + commissionId + " has no delivered preview awaiting customer approval"
                    );
                });

        Instant now = Instant.now();

        // AC02: Mark card ACCEPTED, set approved_submission_id, transition PREVIEW_DELIVERED -> COMPLETED
        submission.acceptByCustomer();
        CommissionSubmissionEntity savedSubmission = commissionSubmissionRepository.save(submission);

        commission.complete(savedSubmission.getSubmissionId());
        CommissionRequestEntity savedCommission = commissionRequestRepository.save(commission);

        CommissionMessageEntity systemMessage = commissionMessageRepository.save(
                CommissionMessageEntity.createSystemMessage(
                        commissionId,
                        "Customer approved preview version " + savedSubmission.getVersionNumber()
                                + ". The commission is now completed."
                )
        );
        CommissionMessageResponse systemMessageResponse = commissionMessageMapper.toResponse(systemMessage);
        chatBroadcastPort.broadcastMessage(commissionId, systemMessageResponse);

        // AC02: Publish CommissionCompletedEvent
        eventPublisher.publishEvent(new CommissionCompletedEvent(
                savedCommission.getCommissionId(),
                savedCommission.getCustomerId(),
                savedSubmission.getSubmissionId(),
                savedCommission.getAssignedStaffId(),
                savedCommission.getAssignedDesignerId(),
                CommissionCompletionSource.CUSTOMER_APPROVED,
                now
        ));

        log.info("Deliverable card approved, commission completed [commissionId={}] [submissionId={}] [messageId={}]",
                commissionId, savedSubmission.getSubmissionId(), systemMessage.getMessageId());

        return ApproveDeliverableCardResponse.builder()
                .commissionId(savedCommission.getCommissionId())
                .commissionStatus(savedCommission.getStatus())
                .approvedSubmissionId(savedSubmission.getSubmissionId())
                .versionNumber(savedSubmission.getVersionNumber())
                .submissionStatus(savedSubmission.getStatus())
                .completedAt(now)
                .systemMessage(systemMessageResponse)
                .build();
    }

    /**
     * The Deliverable Card is backed by the STAFF_APPROVED submission recorded by CMS-17 in
     * {@code approved_submission_id}; falls back to the latest STAFF_APPROVED submission for legacy rows.
     */
    private Optional<CommissionSubmissionEntity> resolveDeliveredSubmission(CommissionRequestEntity commission) {
        Long commissionId = commission.getCommissionId();
        Optional<CommissionSubmissionEntity> candidate = commission.getApprovedSubmissionId() != null
                ? commissionSubmissionRepository.findBySubmissionIdAndCommissionId(commission.getApprovedSubmissionId(), commissionId)
                : commissionSubmissionRepository.findTopByCommissionIdAndStatusOrderByVersionNumberDesc(
                        commissionId, CommissionSubmissionStatus.STAFF_APPROVED);
        return candidate.filter(CommissionSubmissionEntity::isStaffApproved);
    }
}
