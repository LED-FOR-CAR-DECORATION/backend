package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumintra.backend.commission.api.response.RejectRevisionCardResponse;
import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RejectRevisionCardUseCase {
    private static final Logger log = LoggerFactory.getLogger(RejectRevisionCardUseCase.class);

    private final CommissionRequestRepository commissions;
    private final CommissionMessageRepository messages;
    private final CommissionMessageMapper mapper;
    private final CommissionChatBroadcastPort broadcast;
    private final ObjectMapper json;
    private final CommissionRevisionRequestRecorder revisionRequests;

    @Transactional
    public RejectRevisionCardResponse execute(
            Long id, Long customerId, Long messageId, String reason) {
        var commission =
                commissions
                        .findForRevisionConfirmation(id)
                        .orElseThrow(
                                () ->
                                        new CommissionNotFoundException(
                                                "Commission request not found."));
        if (!commission.isOwnedBy(customerId)) {
            throw new CommissionOwnershipMismatchException(
                    "Only the owning customer can reject a revision card.");
        }
        if (commission.getStatus() != CommissionStatus.PREVIEW_DELIVERED) {
            throw conflict("Revision rejection requires PREVIEW_DELIVERED status.");
        }
        var card =
                messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                                id, CommissionMessageType.EXTRA_FEE_CARD)
                        .orElseThrow(() -> conflict("No revision brief card is available."));
        if (!Objects.equals(card.getMessageId(), messageId)) {
            throw conflict("Only the latest revision brief card can be rejected.");
        }
        var preview =
                messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                        id, CommissionMessageType.DELIVERABLE_CARD);
        if (preview.isPresent() && preview.get().getMessageId() > card.getMessageId()) {
            throw conflict("Revision card belongs to an earlier preview.");
        }

        ObjectNode metadata;
        try {
            var node = json.readTree(card.getMetadataJson());
            if (!(node instanceof ObjectNode object)) {
                throw conflict("Invalid revision card metadata.");
            }
            metadata = object;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw conflict("Invalid revision card metadata.");
        }
        if (metadata.hasNonNull("status") && !"PENDING".equals(metadata.path("status").asText())) {
            throw conflict("Revision card has already been processed.");
        }

        int revisionNumber = commission.getRevisionsUsed() + 1;
        var revision = revisionRequests.forCard(card, metadata, revisionNumber);
        if (!revision.isPending()) {
            throw conflict("Revision card has already been processed.");
        }

        metadata.put("status", "REJECTED");
        String trimmedReason = reason != null ? reason.trim() : "";
        if (!trimmedReason.isEmpty()) {
            metadata.put("rejectedReason", trimmedReason);
        } else {
            metadata.remove("rejectedReason");
        }
        metadata.put("rejectedAt", Instant.now().toString());

        String rejectedMetadata;
        try {
            rejectedMetadata = json.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize rejected revision card.", ex);
        }

        revision.reject();
        card.setMetadataJson(rejectedMetadata);
        revisionRequests.save(revision);
        var savedCard = messages.save(card);
        broadcast.broadcastMessage(id, mapper.toResponse(savedCard));

        log.info(
                "Revision card rejected by customer [commissionId={}] [messageId={}]"
                        + " [revisionRequestId={}]",
                id,
                messageId,
                revision.getRevisionRequestId());

        return new RejectRevisionCardResponse(id, messageId, commission.getStatus());
    }

    private InvalidCommissionStatusTransitionException conflict(String message) {
        return new InvalidCommissionStatusTransitionException(message);
    }
}
