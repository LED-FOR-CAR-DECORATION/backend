package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.RejectCommissionDraftRequest;
import com.lumintra.backend.commission.api.response.RejectCommissionDraftResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.CommissionSubmissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RejectCommissionDraftUseCase {

    private static final Logger log = LoggerFactory.getLogger(RejectCommissionDraftUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final CommissionRequestMapper commissionRequestMapper;

    @Transactional
    public RejectCommissionDraftResponse execute(
            Long commissionId,
            RejectCommissionDraftRequest request,
            Long callerStaffId,
            boolean isPrivilegedManagement) {
        log.info("Processing internal draft rejection [commissionId={}] [submissionId={}] [callerStaffId={}] [isPrivileged={}]",
                commissionId, request.submissionId(), callerStaffId, isPrivilegedManagement);

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException(
                        "Commission request not found with id: " + commissionId));

        // AC01: Restrict access to STAFF role and enforce assigned_staff_id lock
        if (commission.getAssignedStaffId() != null
                && !commission.getAssignedStaffId().equals(callerStaffId)
                && !isPrivilegedManagement) {
            log.warn("Commission staff lock violation during internal draft rejection [commissionId={}] [assignedStaffId={}] [callerStaffId={}]",
                    commissionId, commission.getAssignedStaffId(), callerStaffId);
            throw new CommissionStaffLockException(
                    "Commission request " + commissionId + " is locked by assigned staff " + commission.getAssignedStaffId()
            );
        }

        // AC02: Verify commission is in INTERNAL_REVIEW status
        if (commission.getStatus() != CommissionStatus.INTERNAL_REVIEW) {
            log.warn("Invalid commission status for internal draft rejection [commissionId={}] [currentStatus={}]",
                    commissionId, commission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot reject draft internally for commission in status " + commission.getStatus()
                            + " (must be INTERNAL_REVIEW)"
            );
        }

        // Locate target draft submission (explicit submissionId or latest PENDING_STAFF_REVIEW)
        CommissionSubmissionEntity submission = resolveTargetSubmission(commissionId, request.submissionId());

        if (submission.getStatus() != CommissionSubmissionStatus.PENDING_STAFF_REVIEW) {
            log.warn("Invalid submission status for internal rejection [commissionId={}] [submissionId={}] [submissionStatus={}]",
                    commissionId, submission.getSubmissionId(), submission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot reject submission " + submission.getSubmissionId()
                            + " in status " + submission.getStatus()
                            + " (must be PENDING_STAFF_REVIEW)"
            );
        }

        // Auto-assign staff if unassigned
        if (commission.getAssignedStaffId() == null) {
            commission.setAssignedStaffId(callerStaffId);
        }

        // AC02: Mark draft STAFF_REJECTED, consume 0 customer revision quota, transition to INTERNAL_REVISION_REQUIRED
        Instant reviewedAt = Instant.now();
        submission.setStatus(CommissionSubmissionStatus.STAFF_REJECTED);
        submission.setReviewerStaffId(callerStaffId);
        submission.setStaffReviewNote(request.reviewNote().trim());
        submission.setReviewedAt(reviewedAt);
        CommissionSubmissionEntity savedSubmission = commissionSubmissionRepository.save(submission);

        commission.setStatus(CommissionStatus.INTERNAL_REVISION_REQUIRED);
        CommissionRequestEntity savedCommission = commissionRequestRepository.save(commission);

        log.info("Commission draft rejected internally [commissionId={}] [submissionId={}] [versionNumber={}] [revisionsUsed={}] [commissionStatus={}]",
                savedCommission.getCommissionId(), savedSubmission.getSubmissionId(),
                savedSubmission.getVersionNumber(), savedCommission.getRevisionsUsed(),
                savedCommission.getStatus());

        return commissionRequestMapper.toRejectDraftResponse(savedSubmission, savedCommission);
    }

    private CommissionSubmissionEntity resolveTargetSubmission(Long commissionId, Long submissionId) {
        if (submissionId != null) {
            return commissionSubmissionRepository.findBySubmissionIdAndCommissionId(submissionId, commissionId)
                    .orElseThrow(() -> new CommissionSubmissionNotFoundException(
                            "Commission submission " + submissionId + " not found for commission " + commissionId));
        }
        return commissionSubmissionRepository
                .findTopByCommissionIdAndStatusOrderByVersionNumberDesc(
                        commissionId, CommissionSubmissionStatus.PENDING_STAFF_REVIEW)
                .orElseThrow(() -> new CommissionSubmissionNotFoundException(
                        "No pending draft submission found for commission " + commissionId));
    }
}
