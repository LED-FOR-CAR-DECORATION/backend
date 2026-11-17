package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.api.response.PostDeliverableCardResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CMS-17: Staff approves the Designer's pending draft and posts an interactive
 * Deliverable Card to the customer chat (BR-FL3-03).
 */
@Service
@RequiredArgsConstructor
public class PostDeliverableCardUseCase {

    private static final Logger log = LoggerFactory.getLogger(PostDeliverableCardUseCase.class);
    public static final Duration AUTO_COMPLETE_WINDOW = Duration.ofDays(7);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final CommissionMessageRepository commissionMessageRepository;
    private final CommissionMessageMapper commissionMessageMapper;
    private final CommissionChatBroadcastPort chatBroadcastPort;
    private final ObjectMapper objectMapper;

    @Transactional
    public PostDeliverableCardResponse execute(Long commissionId, Long callerStaffId, boolean isPrivilegedManagement) {
        log.info("Processing deliverable card post [commissionId={}] [callerStaffId={}] [isPrivileged={}]",
                commissionId, callerStaffId, isPrivilegedManagement);

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        // AC01: Restrict access to the assigned STAFF (assigned_staff_id lock, MANAGER/ADMIN override)
        if (commission.getAssignedStaffId() != null
                && !commission.getAssignedStaffId().equals(callerStaffId)
                && !isPrivilegedManagement) {
            log.warn("Commission staff lock violation during deliverable card post [commissionId={}] [assignedStaffId={}] [callerStaffId={}]",
                    commissionId, commission.getAssignedStaffId(), callerStaffId);
            throw new CommissionStaffLockException(
                    "Commission request " + commissionId + " is locked by assigned staff " + commission.getAssignedStaffId()
            );
        }

        // AC02: Only INTERNAL_REVIEW can transition to PREVIEW_DELIVERED
        if (commission.getStatus() != CommissionStatus.INTERNAL_REVIEW) {
            log.warn("Invalid commission status for deliverable card post [commissionId={}] [currentStatus={}]",
                    commissionId, commission.getStatus());
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot post deliverable card for commission in status " + commission.getStatus()
                            + " (must be INTERNAL_REVIEW)"
            );
        }

        CommissionSubmissionEntity draft = commissionSubmissionRepository
                .findTopByCommissionIdOrderByVersionNumberDesc(commissionId)
                .filter(CommissionSubmissionEntity::isPendingStaffReview)
                .orElseThrow(() -> {
                    log.warn("No pending draft found for deliverable card post [commissionId={}]", commissionId);
                    return new InvalidCommissionStatusTransitionException(
                            "Commission request " + commissionId + " has no draft pending staff review"
                    );
                });

        if (commission.getAssignedStaffId() == null) {
            commission.setAssignedStaffId(callerStaffId);
        }

        Instant now = Instant.now();
        Instant autoCompleteAt = now.plus(AUTO_COMPLETE_WINDOW);

        // AC02: Mark draft STAFF_APPROVED
        draft.approveByStaff(callerStaffId, now);
        CommissionSubmissionEntity savedDraft = commissionSubmissionRepository.save(draft);

        // AC02: Insert DELIVERABLE_CARD message
        CommissionMessageEntity card = CommissionMessageEntity.createDeliverableCard(
                commissionId,
                callerStaffId,
                "Preview version " + savedDraft.getVersionNumber() + " is ready for your review.",
                savedDraft.getImageUrl(),
                buildCardMetadata(commission, savedDraft, autoCompleteAt)
        );
        CommissionMessageEntity savedCard = commissionMessageRepository.save(card);

        // AC02: Set auto_complete_at (+7d) and transition INTERNAL_REVIEW -> PREVIEW_DELIVERED
        commission.deliverPreview(savedDraft.getSubmissionId(), autoCompleteAt);
        CommissionRequestEntity savedCommission = commissionRequestRepository.save(commission);

        CommissionMessageResponse cardResponse = commissionMessageMapper.toResponse(savedCard);
        chatBroadcastPort.broadcastMessage(commissionId, cardResponse);

        log.info("Deliverable card posted [commissionId={}] [submissionId={}] [messageId={}] [autoCompleteAt={}]",
                commissionId, savedDraft.getSubmissionId(), savedCard.getMessageId(), autoCompleteAt);

        return PostDeliverableCardResponse.builder()
                .commissionId(savedCommission.getCommissionId())
                .commissionStatus(savedCommission.getStatus())
                .approvedSubmissionId(savedDraft.getSubmissionId())
                .versionNumber(savedDraft.getVersionNumber())
                .submissionStatus(savedDraft.getStatus())
                .autoCompleteAt(savedCommission.getAutoCompleteAt())
                .deliverableCard(cardResponse)
                .build();
    }

    private String buildCardMetadata(
            CommissionRequestEntity commission,
            CommissionSubmissionEntity draft,
            Instant autoCompleteAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("submissionId", draft.getSubmissionId());
        metadata.put("versionNumber", draft.getVersionNumber());
        metadata.put("imageUrl", draft.getImageUrl());
        metadata.put("revisionsUsed", commission.getRevisionsUsed());
        metadata.put("maxRevisions", commission.getMaxRevisions());
        metadata.put("autoCompleteAt", autoCompleteAt.toString());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to serialize deliverable card metadata for commission " + commission.getCommissionId(), ex);
        }
    }
}
