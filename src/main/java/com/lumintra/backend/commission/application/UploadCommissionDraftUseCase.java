package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UploadCommissionDraftRequest;
import com.lumintra.backend.commission.api.response.UploadCommissionDraftResponse;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.media.domain.model.MediaPurpose;
import com.lumintra.backend.media.domain.service.PrivateMediaReferenceValidator;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UploadCommissionDraftUseCase {

    private static final Logger log = LoggerFactory.getLogger(UploadCommissionDraftUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final CommissionRevisionRequestRepository commissionRevisionRequestRepository;
    private final PrivateMediaReferenceValidator privateMediaReferenceValidator;
    private final CommissionRequestMapper commissionRequestMapper;

    @Transactional
    public UploadCommissionDraftResponse execute(
            Long commissionId,
            UploadCommissionDraftRequest request,
            Long callerDesignerId) {
        log.info("Processing commission draft upload [commissionId={}] [callerDesignerId={}]",
                commissionId, callerDesignerId);

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException(
                        "Commission request not found with id: " + commissionId));

        // AC01: Enforce assigned_designer_id ownership
        if (commission.getAssignedDesignerId() == null
                || !commission.getAssignedDesignerId().equals(callerDesignerId)) {
            log.warn("Designer ownership violation during draft upload [commissionId={}] [assignedDesignerId={}] [callerDesignerId={}]",
                    commissionId, commission.getAssignedDesignerId(), callerDesignerId);
            throw new CommissionOwnershipMismatchException(
                    "Commission request " + commissionId + " is not assigned to designer " + callerDesignerId
            );
        }

        // AC03: Enforce lifecycle state precondition (IN_PROGRESS or INTERNAL_REVISION_REQUIRED)
        if (commission.getStatus() != CommissionStatus.IN_PROGRESS
                && commission.getStatus() != CommissionStatus.INTERNAL_REVISION_REQUIRED) {
            log.warn("Invalid commission status for draft upload [commissionId={}] [currentStatus={}]",
                    commissionId, commission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot upload draft for commission in status " + commission.getStatus()
                            + " (must be IN_PROGRESS or INTERNAL_REVISION_REQUIRED)"
            );
        }

        // AC02: Must be a COMMISSION_DRAFT upload for this commission; the object key is persisted
        String draftObjectKey = privateMediaReferenceValidator.requireOwnedObjectKey(
                request.imageUrl(), MediaPurpose.COMMISSION_DRAFT, commissionId);

        int nextVersionNumber = commissionSubmissionRepository
                .findTopByCommissionIdOrderByVersionNumberDesc(commissionId)
                .map(latest -> latest.getVersionNumber() + 1)
                .orElse(1);

        CommissionSubmissionEntity draft = CommissionSubmissionEntity.createDraft(
                commissionId,
                callerDesignerId,
                nextVersionNumber,
                draftObjectKey,
                request.designerNote()
        );
        commissionRevisionRequestRepository
                .findFirstByCommissionIdAndStatusInOrderByRevisionRequestIdDesc(
                        commissionId, CommissionRevisionRequestStatus.designerActiveStatuses())
                .ifPresent(revision -> draft.setRevisionRequestId(revision.getRevisionRequestId()));
        CommissionSubmissionEntity savedDraft = commissionSubmissionRepository.save(draft);

        // AC03: Transition status to INTERNAL_REVIEW
        commission.setStatus(CommissionStatus.INTERNAL_REVIEW);
        CommissionRequestEntity savedCommission = commissionRequestRepository.save(commission);

        log.info("Commission draft uploaded successfully [commissionId={}] [submissionId={}] [versionNumber={}] [commissionStatus={}]",
                savedCommission.getCommissionId(), savedDraft.getSubmissionId(),
                savedDraft.getVersionNumber(), savedCommission.getStatus());

        UploadCommissionDraftResponse response = commissionRequestMapper.toUploadDraftResponse(savedDraft, savedCommission);
        if (response.createdAt() == null) {
            response = UploadCommissionDraftResponse.builder()
                    .submissionId(response.submissionId())
                    .commissionId(response.commissionId())
                    .designerId(response.designerId())
                    .versionNumber(response.versionNumber())
                    .imageUrl(response.imageUrl())
                    .designerNote(response.designerNote())
                    .submissionStatus(response.submissionStatus())
                    .commissionStatus(response.commissionStatus())
                    .createdAt(Instant.now())
                    .build();
        }
        return response;
    }
}
