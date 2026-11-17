package com.lumintra.backend.commission.application.listener;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
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

import java.util.Optional;

/**
 * Listener for PaymentSucceededEvent to handle COMMISSION_INITIAL payments.
 * Unlocks the commission workflow and chat room (AC01, AC02, AC03).
 */
@Component
@RequiredArgsConstructor
public class CommissionPaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(CommissionPaymentEventListener.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionMessageRepository commissionMessageRepository;

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null) {
            return;
        }

        if (event.targetType() != PaymentTargetType.COMMISSION_INITIAL) {
            log.debug("PaymentSucceededEvent ignored by CommissionPaymentEventListener: targetType is {} [paymentId={}] [targetId={}]",
                    event.targetType(), event.paymentId(), event.targetId());
            return;
        }

        Long commissionId = event.targetId();
        if (commissionId == null) {
            log.warn("PaymentSucceededEvent ignored: targetId is null [paymentId={}]", event.paymentId());
            return;
        }

        Optional<CommissionRequestEntity> commissionOpt = commissionRequestRepository.findById(commissionId);
        if (commissionOpt.isEmpty()) {
            log.warn("Commission request not found for PaymentSucceededEvent [commissionId={}] [paymentId={}]",
                    commissionId, event.paymentId());
            return;
        }

        CommissionRequestEntity commission = commissionOpt.get();

        // Idempotency check: if commission is already at PAID or subsequent active stage, skip duplicate processing
        if (commission.getStatus() != CommissionStatus.PENDING_PAYMENT) {
            log.info("Commission payment transition skipped: commission is in non-pending status {} [commissionId={}] [paymentId={}]",
                    commission.getStatus(), commissionId, event.paymentId());
            return;
        }

        // AC01: Clear active_payment_code on CommissionRequestEntity
        commission.setActivePaymentCode(null);

        // AC02: Transition status from PENDING_PAYMENT to PAID
        commission.setStatus(CommissionStatus.PAID);
        commissionRequestRepository.save(commission);

        log.info("Commission initial payment processed: status transitioned to PAID and activePaymentCode cleared [commissionId={}] [paymentId={}]",
                commissionId, event.paymentId());

        // AC03: Insert a SYSTEM message into COMMISSION_MESSAGE
        String amountDisplay = event.amount() != null
                ? event.amount().stripTrailingZeros().toPlainString() + " VND"
                : "the required deposit";
        String messageContent = "Payment confirmed. Initial deposit of " + amountDisplay + " received. Commission workflow and chat room are now unlocked.";

        CommissionMessageEntity systemMessage = CommissionMessageEntity.createSystemMessage(commissionId, messageContent);
        commissionMessageRepository.save(systemMessage);

        log.info("Commission system message created for initial payment [commissionId={}] [messageId={}]",
                commissionId, systemMessage.getMessageId());
    }
}
