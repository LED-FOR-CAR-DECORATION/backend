package com.lumintra.backend.commission.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Undoes an unpaid extra-revision checkout, putting the revision brief back in front of the customer:
 * used when the customer cancels the checkout (CMS-22) and when the payment session expires (CMS-26).
 *
 * <p>The caller holds the commission row lock and has checked that the commission is
 * AWAITING_EXTRA_PAYMENT with {@code activePaymentCode} equal to {@code paymentCode}.
 */
@Service
@RequiredArgsConstructor
public class RevisionCheckoutReverter {

    /** CMS-26 AC03: the customer always gets at least this long to respond after the checkout is undone. */
    public static final Duration MIN_RESPONSE_WINDOW_AFTER_REVERT = Duration.ofHours(24);

    private final CommissionRequestRepository commissions;
    private final CommissionMessageRepository messages;
    private final CommissionMessageMapper mapper;
    private final CommissionChatBroadcastPort broadcast;
    private final ObjectMapper json;
    private final CommissionRevisionRequestRecorder revisionRequests;

    /** Why the checkout is being undone; names the card metadata key that keeps the old payment code. */
    public enum Reason {
        CANCELLED("cancelledPaymentCode"),
        EXPIRED("expiredPaymentCode");

        private final String metadataKey;

        Reason(String metadataKey) {
            this.metadataKey = metadataKey;
        }
    }

    /**
     * Revision request back to PENDING, card back to PENDING (old code kept under {@code cancelledPaymentCode}
     * or {@code expiredPaymentCode}), unpaid fee removed, status back to PREVIEW_DELIVERED, and the
     * auto-complete deadline pushed to at least {@code now + 24h}. Nothing is changed when the card or the
     * revision request does not match the pending checkout.
     *
     * @throws InvalidCommissionStatusTransitionException when the card or revision request does not match
     */
    public void revert(CommissionRequestEntity commission, String paymentCode, Reason reason, Instant now) {
        Long id = commission.getCommissionId();
        var card = messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(id, CommissionMessageType.EXTRA_FEE_CARD)
                .orElseThrow(() -> conflict("Confirmed revision card is missing."));
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
        var fee = metadata.path("revisionFee");
        if (!"CONFIRMED".equals(metadata.path("status").asText())
                || metadata.path("usesFreeQuota").asBoolean(false)
                || !fee.isNumber()
                || fee.decimalValue().signum() <= 0
                || commission.getExtraRevisionFee().compareTo(fee.decimalValue()) < 0
                || (metadata.hasNonNull("paymentCode")
                        && !paymentCode.equalsIgnoreCase(metadata.path("paymentCode").asText()))) {
            throw conflict("Card does not match the pending paid revision checkout.");
        }
        var revision = revisionRequests.forCard(card, metadata, commission.getRevisionsUsed() + 1);
        if (revision.getStatus() != CommissionRevisionRequestStatus.AWAITING_PAYMENT && !revision.isPending()) {
            throw conflict("Revision request is not awaiting payment.");
        }
        revision.reopen();
        metadata.put("status", "PENDING");
        metadata.remove("paymentCode");
        metadata.put(reason.metadataKey, paymentCode);
        String revertedMetadata;
        try {
            revertedMetadata = json.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize reverted revision card.", ex);
        }
        commission.setExtraRevisionFee(commission.getExtraRevisionFee().subtract(fee.decimalValue()));
        commission.setTotalPrice(commission.getInitialPrice().add(commission.getExtraRevisionFee()));
        commission.setActivePaymentCode(null);
        commission.setStatus(CommissionStatus.PREVIEW_DELIVERED);
        commission.setAutoCompleteAt(responseDeadline(commission.getAutoCompleteAt(), now));
        card.setMetadataJson(revertedMetadata);
        commissions.save(commission);
        revisionRequests.save(revision);
        var savedCard = messages.save(card);
        broadcast.broadcastMessage(id, mapper.toResponse(savedCard));
    }

    /** CMS-26 AC03: {@code max(current, now + 24h)} so the CMS-27 job can't complete it before the customer responds. */
    static Instant responseDeadline(Instant current, Instant now) {
        Instant earliest = now.plus(MIN_RESPONSE_WINDOW_AFTER_REVERT);
        return current != null && current.isAfter(earliest) ? current : earliest;
    }

    private static InvalidCommissionStatusTransitionException conflict(String message) {
        return new InvalidCommissionStatusTransitionException(message);
    }
}
