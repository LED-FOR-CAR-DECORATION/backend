package com.lumintra.backend.commission.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.domain.event.CommissionCompletedEvent;
import com.lumintra.backend.commission.domain.model.CommissionCompletionSource;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Finalizes a commission whose preview was delivered: CMS-18 (customer approves the Deliverable Card)
 * and CMS-27 (no response before {@code auto_complete_at}). Both paths produce the same result and
 * publish {@link CommissionCompletedEvent} exactly once; only the source and the chat text differ.
 *
 * <p>Callers must hold the commission row lock and run inside a transaction.
 */
@Service
@RequiredArgsConstructor
public class CommissionCompletionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionCompletionService.class);
    private static final String PENDING = "PENDING";
    private static final String EXPIRED = "EXPIRED";

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final CommissionMessageRepository commissionMessageRepository;
    private final CommissionRevisionRequestRepository revisionRequestRepository;
    private final CommissionMessageMapper commissionMessageMapper;
    private final CommissionChatBroadcastPort chatBroadcastPort;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper json;

    /**
     * Accepts the delivered preview, moves PREVIEW_DELIVERED to COMPLETED, posts the SYSTEM chat message and
     * publishes {@link CommissionCompletedEvent}. For {@code AUTO_COMPLETED} a revision brief still waiting for
     * the customer is expired first (CMS-27 AC04).
     *
     * @throws InvalidCommissionStatusTransitionException when the commission is not PREVIEW_DELIVERED or has no
     *         STAFF_APPROVED preview awaiting the customer
     */
    public Completion complete(CommissionRequestEntity commission, CommissionCompletionSource source, Instant now) {
        Long commissionId = commission.getCommissionId();
        if (commission.getStatus() != CommissionStatus.PREVIEW_DELIVERED) {
            throw new InvalidCommissionStatusTransitionException(
                    "Cannot complete commission in status " + commission.getStatus() + " (must be PREVIEW_DELIVERED)");
        }
        CommissionSubmissionEntity submission = resolveDeliveredSubmission(commission)
                .orElseThrow(() -> {
                    log.warn("No delivered preview found to complete commission [commissionId={}] [approvedSubmissionId={}] [source={}]",
                            commissionId, commission.getApprovedSubmissionId(), source);
                    return new InvalidCommissionStatusTransitionException(
                            "Commission request " + commissionId + " has no delivered preview awaiting customer approval");
                });

        if (source == CommissionCompletionSource.AUTO_COMPLETED) {
            expirePendingRevisionBrief(commissionId);
        }

        submission.acceptByCustomer();
        CommissionSubmissionEntity savedSubmission = commissionSubmissionRepository.save(submission);
        commission.complete(savedSubmission.getSubmissionId());
        CommissionRequestEntity savedCommission = commissionRequestRepository.save(commission);

        CommissionMessageEntity systemMessage = commissionMessageRepository.save(
                CommissionMessageEntity.createSystemMessage(commissionId, completionText(source, savedSubmission)));
        CommissionMessageResponse systemMessageResponse = commissionMessageMapper.toResponse(systemMessage);
        chatBroadcastPort.broadcastMessage(commissionId, systemMessageResponse);

        eventPublisher.publishEvent(new CommissionCompletedEvent(
                savedCommission.getCommissionId(),
                savedCommission.getCustomerId(),
                savedSubmission.getSubmissionId(),
                savedCommission.getAssignedStaffId(),
                savedCommission.getAssignedDesignerId(),
                source,
                now));

        log.info("Commission completed [commissionId={}] [submissionId={}] [source={}] [messageId={}]",
                commissionId, savedSubmission.getSubmissionId(), source, systemMessage.getMessageId());
        return new Completion(savedCommission, savedSubmission, systemMessageResponse, now);
    }

    private static String completionText(CommissionCompletionSource source, CommissionSubmissionEntity submission) {
        if (source == CommissionCompletionSource.AUTO_COMPLETED) {
            return "Không có phản hồi trong vòng 7 ngày — bản xem trước phiên bản " + submission.getVersionNumber()
                    + " đã được tự động chấp nhận. Yêu cầu thiết kế hiện đã hoàn tất.";
        }
        return "Khách hàng đã phê duyệt bản xem trước phiên bản " + submission.getVersionNumber()
                + ". Yêu cầu thiết kế hiện đã hoàn tất.";
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

    /**
     * CMS-27 AC04: a revision brief the customer never confirmed is superseded, and the latest brief card
     * (if still pending) is marked EXPIRED and re-broadcast so the chat stops offering it.
     */
    private void expirePendingRevisionBrief(Long commissionId) {
        for (CommissionRevisionRequestEntity pending :
                revisionRequestRepository.findAllByCommissionIdAndStatus(commissionId, CommissionRevisionRequestStatus.PENDING)) {
            pending.supersede();
            revisionRequestRepository.save(pending);
            log.info("Pending revision request superseded by auto-completion [commissionId={}] [revisionRequestId={}]",
                    commissionId, pending.getRevisionRequestId());
        }
        commissionMessageRepository
                .findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(commissionId, CommissionMessageType.EXTRA_FEE_CARD)
                .ifPresent(card -> expireCardIfPending(commissionId, card));
    }

    private void expireCardIfPending(Long commissionId, CommissionMessageEntity card) {
        ObjectNode metadata;
        try {
            if (!(json.readTree(card.getMetadataJson() == null ? "{}" : card.getMetadataJson()) instanceof ObjectNode node)) {
                return;
            }
            metadata = node;
        } catch (JsonProcessingException ex) {
            log.warn("Unreadable revision card metadata, left unchanged [commissionId={}] [messageId={}]",
                    commissionId, card.getMessageId());
            return;
        }
        // Cards without a status were posted as PENDING (ConfirmRevisionCardUseCase treats them the same way)
        if (metadata.hasNonNull("status") && !PENDING.equals(metadata.path("status").asText())) {
            return;
        }
        metadata.put("status", EXPIRED);
        try {
            card.setMetadataJson(json.writeValueAsString(metadata));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize expired revision card.", ex);
        }
        CommissionMessageEntity savedCard = commissionMessageRepository.save(card);
        chatBroadcastPort.broadcastMessage(commissionId, commissionMessageMapper.toResponse(savedCard));
    }

    public record Completion(
            CommissionRequestEntity commission,
            CommissionSubmissionEntity submission,
            CommissionMessageResponse systemMessage,
            Instant completedAt) {
    }
}
