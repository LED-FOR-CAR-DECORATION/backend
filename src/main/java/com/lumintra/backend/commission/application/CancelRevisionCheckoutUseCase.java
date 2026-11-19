package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.domain.model.CancelPaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CancelRevisionCheckoutUseCase {
    private final CommissionRequestRepository commissions;
    private final CommissionMessageRepository messages;
    private final PaymentSessionPort payments;
    private final CommissionMessageMapper mapper;
    private final CommissionChatBroadcastPort broadcast;
    private final ObjectMapper json;
    private final CommissionRevisionRequestRecorder revisionRequests;

    @Transactional
    public void execute(Long id, Long customerId) {
        var snapshot =
                commissions
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new CommissionNotFoundException(
                                                "Commission request not found."));
        validate(snapshot, customerId);
        String code = snapshot.getActivePaymentCode();
        // Payment updates precede the commission lock, matching the webhook's lock order.
        payments.cancelSession(
                new CancelPaymentSessionCommand(
                        code, PaymentTargetType.COMMISSION_REVISION, id, customerId));
        var commission =
                commissions
                        .findByIdForUpdate(id)
                        .orElseThrow(
                                () ->
                                        new CommissionNotFoundException(
                                                "Commission request not found."));
        validate(commission, customerId);
        if (!Objects.equals(code, commission.getActivePaymentCode())) {
            throw conflict("Active revision checkout changed before cancellation.");
        }
        var card =
                messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                                id, CommissionMessageType.EXTRA_FEE_CARD)
                        .orElseThrow(() -> conflict("Confirmed revision card is missing."));
        ObjectNode metadata;
        try {
            var node = json.readTree(card.getMetadataJson());
            if (!(node instanceof ObjectNode object))
                throw conflict("Invalid revision card metadata.");
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
                        && !code.equalsIgnoreCase(metadata.path("paymentCode").asText()))) {
            throw conflict("Card does not match the pending paid revision checkout.");
        }
        var revision =
                revisionRequests.forCard(card, metadata, commission.getRevisionsUsed() + 1);
        if (revision.getStatus() != CommissionRevisionRequestStatus.AWAITING_PAYMENT
                && !revision.isPending()) {
            throw conflict("Revision request is not awaiting payment.");
        }
        revision.reopen();
        metadata.put("status", "PENDING");
        metadata.remove("paymentCode");
        metadata.put("cancelledPaymentCode", code);
        String cancelledMetadata;
        try {
            cancelledMetadata = json.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize cancelled revision card.", ex);
        }
        commission.setExtraRevisionFee(
                commission.getExtraRevisionFee().subtract(fee.decimalValue()));
        commission.setTotalPrice(
                commission.getInitialPrice().add(commission.getExtraRevisionFee()));
        commission.setActivePaymentCode(null);
        commission.setStatus(CommissionStatus.PREVIEW_DELIVERED);
        card.setMetadataJson(cancelledMetadata);
        commissions.save(commission);
        revisionRequests.save(revision);
        var savedCard = messages.save(card);
        broadcast.broadcastMessage(id, mapper.toResponse(savedCard));
    }

    private void validate(CommissionRequestEntity commission, Long customerId) {
        if (!commission.isOwnedBy(customerId)) {
            throw new CommissionOwnershipMismatchException(
                    "Only the owning customer can cancel the revision checkout.");
        }
        if (commission.getStatus() != CommissionStatus.AWAITING_EXTRA_PAYMENT
                || commission.getActivePaymentCode() == null
                || commission.getActivePaymentCode().isBlank()) {
            throw conflict("Commission has no pending extra revision checkout.");
        }
    }

    private InvalidCommissionStatusTransitionException conflict(String message) {
        return new InvalidCommissionStatusTransitionException(message);
    }
}
