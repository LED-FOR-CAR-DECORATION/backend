package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.ReviewCommissionSubmissionRequest;
import com.lumintra.backend.commission.api.response.CommissionSubmissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReviewCommissionSubmissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReviewCommissionSubmissionUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final CommissionMessageRepository commissionMessageRepository;
    private final EmployeeLookupService employeeLookupService;

    @Transactional
    public CommissionSubmissionResponse execute(
            Long commissionId,
            Long submissionId,
            ReviewCommissionSubmissionRequest request,
            Long callerStaffId,
            boolean isPrivileged
    ) {
        log.info("Processing staff submission review [commissionId={}] [submissionId={}] [action={}] [callerStaffId={}]",
                commissionId, submissionId, request.action(), callerStaffId);

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        if (commission.getAssignedStaffId() != null
                && !commission.getAssignedStaffId().equals(callerStaffId)
                && !isPrivileged) {
            log.warn("Staff lock violation during submission review [commissionId={}] [assignedStaffId={}] [callerStaffId={}]",
                    commissionId, commission.getAssignedStaffId(), callerStaffId);
            throw new CommissionStaffLockException(
                    "Commission request " + commissionId + " is locked by assigned staff " + commission.getAssignedStaffId()
            );
        }

        CommissionSubmissionEntity submission = commissionSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission submission not found with id: " + submissionId));

        if (!submission.getCommissionId().equals(commissionId)) {
            throw new CommissionNotFoundException("Submission " + submissionId + " does not belong to commission " + commissionId);
        }

        Instant now = Instant.now();
        submission.setReviewerStaffId(callerStaffId);
        submission.setReviewedAt(now);

        if (request.action() == ReviewCommissionSubmissionRequest.ReviewAction.APPROVE) {
            submission.setStatus(CommissionSubmissionStatus.STAFF_APPROVED);
            submission.setDeliveredAt(now);
            commission.setStatus(CommissionStatus.PREVIEW_DELIVERED);
            commission.setApprovedSubmissionId(submissionId);

            // Add deliverable card message to customer chat room
            try {
                commissionMessageRepository.save(CommissionMessageEntity.builder()
                        .commissionId(commissionId)
                        .senderId(callerStaffId)
                        .senderRole(CommissionMessageSenderRole.STAFF)
                        .messageType(CommissionMessageType.DELIVERABLE_CARD)
                        .content("Staff đã kiểm duyệt và gửi bản phác thảo v" + submission.getVersionNumber() + " tới quý khách.")
                        .mediaUrl(submission.getImageUrl())
                        .build());
            } catch (Exception msgEx) {
                log.warn("Could not post deliverable message to commission chat [commissionId={}]: {}", commissionId, msgEx.getMessage());
            }

            log.info("Staff approved submission [commissionId={}] [submissionId={}] -> PREVIEW_DELIVERED", commissionId, submissionId);
        } else {
            submission.setStatus(CommissionSubmissionStatus.STAFF_REJECTED);
            submission.setStaffReviewNote(request.staffReviewNote() != null ? request.staffReviewNote().trim() : "Staff yêu cầu chỉnh sửa bản vẽ.");
            commission.setStatus(CommissionStatus.INTERNAL_REVISION_REQUIRED);

            log.info("Staff requested revision for submission [commissionId={}] [submissionId={}] -> INTERNAL_REVISION_REQUIRED", commissionId, submissionId);
        }

        commissionSubmissionRepository.save(submission);
        commissionRequestRepository.save(commission);

        String reviewerName = employeeLookupService.findProfileByEmployeeId(callerStaffId)
                .map(p -> p.fullName())
                .orElse("Staff");

        String designerName = employeeLookupService.findProfileByEmployeeId(submission.getDesignerId())
                .map(p -> p.fullName())
                .orElse("Designer");

        return CommissionSubmissionResponse.builder()
                .submissionId(submission.getSubmissionId())
                .commissionId(commissionId)
                .designerId(submission.getDesignerId())
                .designerName(designerName)
                .versionNumber(submission.getVersionNumber())
                .imageUrl(submission.getImageUrl())
                .designerNote(submission.getDesignerNote())
                .status(submission.getStatus())
                .reviewerStaffId(callerStaffId)
                .reviewerStaffName(reviewerName)
                .staffReviewNote(submission.getStaffReviewNote())
                .reviewedAt(submission.getReviewedAt())
                .deliveredAt(submission.getDeliveredAt())
                .createdAt(submission.getCreatedAt())
                .build();
    }
}

