package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.SubmitCommissionDraftRequest;
import com.lumintra.backend.commission.api.response.CommissionSubmissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubmitCommissionDraftUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitCommissionDraftUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final EmployeeLookupService employeeLookupService;

    @Transactional
    public CommissionSubmissionResponse execute(Long commissionId, SubmitCommissionDraftRequest request, Long callerDesignerId) {
        log.info("Processing designer draft submission [commissionId={}] [callerDesignerId={}]", commissionId, callerDesignerId);

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        if (commission.getAssignedDesignerId() == null || !commission.getAssignedDesignerId().equals(callerDesignerId)) {
            log.warn("Access denied: Caller is not the assigned designer [commissionId={}] [callerDesignerId={}] [assignedDesignerId={}]",
                    commissionId, callerDesignerId, commission.getAssignedDesignerId());
            throw new AccessDeniedException("You are not the designer assigned to this commission ticket.");
        }

        if (commission.getStatus() != CommissionStatus.IN_PROGRESS
                && commission.getStatus() != CommissionStatus.INTERNAL_REVISION_REQUIRED) {
            log.warn("Invalid commission status for draft submission [commissionId={}] [currentStatus={}]",
                    commissionId, commission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot submit draft for commission in status " + commission.getStatus() + " (must be IN_PROGRESS or INTERNAL_REVISION_REQUIRED)"
            );
        }

        List<CommissionSubmissionEntity> existingSubmissions = commissionSubmissionRepository
                .findAllByCommissionIdOrderByVersionNumberAscSubmissionIdAsc(commissionId);

        int nextVersionNumber = existingSubmissions.isEmpty()
                ? 1
                : existingSubmissions.get(existingSubmissions.size() - 1).getVersionNumber() + 1;

        CommissionSubmissionEntity submission = CommissionSubmissionEntity.createDraft(
                commissionId,
                callerDesignerId,
                nextVersionNumber,
                request.imageUrl(),
                request.designerNote()
        );

        CommissionSubmissionEntity savedSubmission = commissionSubmissionRepository.save(submission);

        commission.setStatus(CommissionStatus.INTERNAL_REVIEW);
        commissionRequestRepository.save(commission);

        String designerName = employeeLookupService.findProfileByEmployeeId(callerDesignerId)
                .map(p -> p.fullName())
                .orElse("Designer");

        log.info("Designer draft submitted successfully [commissionId={}] [submissionId={}] [versionNumber={}] [status={}]",
                commissionId, savedSubmission.getSubmissionId(), savedSubmission.getVersionNumber(), commission.getStatus());

        return CommissionSubmissionResponse.builder()
                .submissionId(savedSubmission.getSubmissionId())
                .commissionId(commissionId)
                .designerId(callerDesignerId)
                .designerName(designerName)
                .versionNumber(savedSubmission.getVersionNumber())
                .imageUrl(savedSubmission.getImageUrl())
                .designerNote(savedSubmission.getDesignerNote())
                .status(savedSubmission.getStatus())
                .createdAt(savedSubmission.getCreatedAt())
                .build();
    }
}

