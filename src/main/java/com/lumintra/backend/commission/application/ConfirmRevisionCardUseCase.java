package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumintra.backend.commerce.domain.service.CustomerSubscriptionBonusLookupService;
import com.lumintra.backend.commission.api.response.ConfirmRevisionCardResponse;
import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.CreatePaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ConfirmRevisionCardUseCase {
    private final CommissionRequestRepository commissions;
    private final CommissionMessageRepository messages;
    private final PaymentSessionPort payments;
    private final CommissionMessageMapper mapper;
    private final CommissionChatBroadcastPort broadcast;
    private final ObjectMapper json;
    private final CommissionRevisionRequestRecorder revisionRequests;
    private final CustomerSubscriptionBonusLookupService subscriptionBonusLookupService;

    @Transactional
    public ConfirmRevisionCardResponse execute(Long id, Long customerId, Long messageId) {
        var commission =
                commissions
                        .findForRevisionConfirmation(id)
                        .orElseThrow(
                                () ->
                                        new CommissionNotFoundException(
                                                "Commission request not found."));
        if (!commission.isOwnedBy(customerId)) {
            throw new CommissionOwnershipMismatchException(
                    "Only the owning customer can confirm a revision card.");
        }
        if (commission.getStatus() != CommissionStatus.PREVIEW_DELIVERED) {
            throw conflict("Revision confirmation requires PREVIEW_DELIVERED status.");
        }
        var card =
                messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                                id, CommissionMessageType.EXTRA_FEE_CARD)
                        .orElseThrow(() -> conflict("No revision brief card is available."));
        if (!Objects.equals(card.getMessageId(), messageId)) {
            throw conflict("Only the latest revision brief card can be confirmed.");
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
        boolean usesCommissionQuota = commission.getRevisionsUsed() < commission.getMaxRevisions();
        boolean usesSubscriptionCredit =
                !usesCommissionQuota
                        && subscriptionBonusLookupService.consumeOneExtraCommissionRevision(customerId);
        boolean free = usesCommissionQuota || usesSubscriptionCredit;
        var fee =
                free
                        ? BigDecimal.ZERO.setScale(2)
                        : commission
                                .getInitialPrice()
                                .multiply(new BigDecimal("0.10"))
                                .setScale(2, RoundingMode.HALF_UP);
        metadata.put("status", "CONFIRMED");
        metadata.put("usesFreeQuota", free);
        metadata.put("usesSubscriptionCredit", usesSubscriptionCredit);
        metadata.put("revisionFee", fee);
        metadata.put("feePercentage", free ? 0 : 10);
        metadata.put("revisionsUsed", free ? revisionNumber : commission.getRevisionsUsed());
        String confirmedMetadata;
        try {
            confirmedMetadata = json.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize confirmed revision card.", ex);
        }
        PaymentSessionResponse session = null;
        var now = Instant.now();
        if (free) {
            commission.setRevisionsUsed(revisionNumber);
            // The brief was written by staff and confirmed by the customer: it goes straight to the designer.
            commission.awaitRevisionEstimate(now);
            commission.setActivePaymentCode(null);
            revision.confirmFree(revisionNumber, now);
        } else {
            if (fee.signum() <= 0) {
                throw conflict("Paid revision fee must be greater than zero.");
            }
            session =
                    payments.createSession(
                            new CreatePaymentSessionCommand(
                                    PaymentTargetType.COMMISSION_REVISION,
                                    id,
                                    customerId,
                                    fee,
                                    Duration.ofHours(24)));
            commission.setExtraRevisionFee(commission.getExtraRevisionFee().add(fee));
            commission.setTotalPrice(
                    commission.getInitialPrice().add(commission.getExtraRevisionFee()));
            commission.setActivePaymentCode(session.paymentCode());
            commission.setStatus(CommissionStatus.AWAITING_EXTRA_PAYMENT);
            revision.awaitPayment(revisionNumber, fee, session.paymentCode(), now);
        }
        commission.setAutoCompleteAt(null);
        card.setMetadataJson(confirmedMetadata);
        commissions.save(commission);
        revisionRequests.save(revision);
        var savedCard = messages.save(card);
        broadcast.broadcastMessage(id, mapper.toResponse(savedCard));
        return new ConfirmRevisionCardResponse(
                id,
                messageId,
                commission.getStatus(),
                commission.getRevisionsUsed(),
                commission.getExtraRevisionFee(),
                session);
    }

    private InvalidCommissionStatusTransitionException conflict(String message) {
        return new InvalidCommissionStatusTransitionException(message);
    }
}
