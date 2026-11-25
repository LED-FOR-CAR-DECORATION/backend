package com.lumintra.backend.commission.application.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.domain.event.PaymentSucceededEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CommissionRevisionPaymentEventListener {
    private static final Logger log =
            LoggerFactory.getLogger(CommissionRevisionPaymentEventListener.class);
    private final CommissionRequestRepository commissions;
    private final CommissionMessageRepository messages;
    private final CommissionMessageMapper mapper;
    private final CommissionChatBroadcastPort broadcast;
    private final ObjectMapper json;
    private final CommissionRevisionRequestRecorder revisionRequests;

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null
                || event.targetType() != PaymentTargetType.COMMISSION_REVISION
                || event.targetId() == null
                || event.targetId() <= 0
                || event.paymentCode() == null
                || event.paymentCode().isBlank()) {
            return;
        }
        var commission = commissions.findByIdForUpdate(event.targetId()).orElse(null);
        if (commission == null
                || commission.getStatus() != CommissionStatus.AWAITING_EXTRA_PAYMENT
                || commission.getActivePaymentCode() == null
                || !commission.getActivePaymentCode().equalsIgnoreCase(event.paymentCode())) {
            log.debug(
                    "Revision payment ignored for inactive session [commissionId={}]"
                            + " [paymentId={}]",
                    event.targetId(),
                    event.paymentId());
            return;
        }
        if (event.customerId() != null && !commission.isOwnedBy(event.customerId())) {
            throw new IllegalStateException(
                    "Revision payment customer does not match commission owner.");
        }
        var card =
                messages.findFirstByCommissionIdAndMessageTypeOrderByCreatedAtDescMessageIdDesc(
                                event.targetId(), CommissionMessageType.EXTRA_FEE_CARD)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Confirmed revision card is missing."));
        ObjectNode metadata;
        try {
            var node = json.readTree(card.getMetadataJson());
            if (!(node instanceof ObjectNode object)) {
                throw new IllegalStateException("Invalid revision card metadata.");
            }
            metadata = object;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new IllegalStateException("Unable to read revision card metadata.", ex);
        }
        var fee = metadata.path("revisionFee");
        if (!"CONFIRMED".equals(metadata.path("status").asText())
                || metadata.path("usesFreeQuota").asBoolean(false)
                || !fee.isNumber()
                || fee.decimalValue().signum() <= 0
                || event.amount() == null
                || fee.decimalValue().compareTo(event.amount()) != 0
                || (metadata.hasNonNull("paymentCode")
                        && !metadata.path("paymentCode")
                                .asText()
                                .equalsIgnoreCase(event.paymentCode()))) {
            throw new IllegalStateException("Revision card does not match the successful payment.");
        }
        var paidAt = event.paidAt() != null ? event.paidAt() : Instant.now();
        int revisionNumber = commission.getRevisionsUsed() + 1;
        var revision = revisionRequests.forCard(card, metadata, revisionNumber);
        if (revision.isPending()) {
            // Card confirmed before revision requests were recorded.
            revision.awaitPayment(
                    revisionNumber, fee.decimalValue(), commission.getActivePaymentCode(), paidAt);
        }
        if (revision.getStatus() != CommissionRevisionRequestStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException(
                    "Revision request is not awaiting payment: " + revision.getStatus());
        }
        metadata.put("status", "PAID");
        metadata.put("paymentCode", commission.getActivePaymentCode());
        metadata.put("paymentId", event.paymentId());
        metadata.put("paidAt", paidAt.toString());
        metadata.put("revisionsUsed", revisionNumber);
        String paidMetadata;
        try {
            paidMetadata = json.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize paid revision card.", ex);
        }
        revision.markPaid(paidAt);
        commission.setActivePaymentCode(null);
        // The estimate window starts when the designer receives the revision, not at payment time.
        commission.awaitRevisionEstimate(Instant.now());
        commission.setRevisionsUsed(revisionNumber);
        card.setMetadataJson(paidMetadata);
        commissions.save(commission);
        revisionRequests.save(revision);
        var savedCard = messages.save(card);
        broadcast.broadcastMessage(event.targetId(), mapper.toResponse(savedCard));
        log.info(
                "Revision payment applied [commissionId={}] [paymentId={}] [messageId={}]",
                event.targetId(),
                event.paymentId(),
                card.getMessageId());
    }
}
