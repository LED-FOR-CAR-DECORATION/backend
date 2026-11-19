package com.lumintra.backend.commission.application.service;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.domain.event.PaymentExpiredEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * CMS-26 (#131): applies an expired commission payment session to the commission, in its own transaction
 * under the commission row lock. Stale or unrelated events are ignored rather than treated as errors.
 */
@Service
@RequiredArgsConstructor
public class CommissionPaymentExpiryHandler {

    private static final Logger log = LoggerFactory.getLogger(CommissionPaymentExpiryHandler.class);

    private final CommissionRequestRepository commissions;
    private final RevisionCheckoutReverter reverter;

    public enum Outcome {
        /** AC01: PENDING_PAYMENT moved to EXPIRED. */
        QUOTE_EXPIRED,
        /** AC02: AWAITING_EXTRA_PAYMENT reverted to PREVIEW_DELIVERED with the brief open again. */
        REVISION_CHECKOUT_REVERTED,
        /** AC04/AC05: not a commission payment, or no longer the commission's active checkout. */
        IGNORED
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome handle(PaymentExpiredEvent event) {
        PaymentTargetType type = event.targetType();
        if (type != PaymentTargetType.COMMISSION_INITIAL && type != PaymentTargetType.COMMISSION_REVISION) {
            return Outcome.IGNORED;
        }
        String paymentCode = event.paymentCode();
        if (event.targetId() == null || paymentCode == null || paymentCode.isBlank()) {
            log.warn("Commission payment expiry ignored: missing target or payment code [paymentId={}] [targetType={}] [targetId={}]",
                    event.paymentId(), type, event.targetId());
            return Outcome.IGNORED;
        }
        CommissionRequestEntity commission = commissions.findByIdForUpdate(event.targetId()).orElse(null);
        if (commission == null) {
            log.warn("Commission payment expiry ignored: commission not found [commissionId={}] [paymentCode={}]",
                    event.targetId(), paymentCode);
            return Outcome.IGNORED;
        }
        String activeCode = commission.getActivePaymentCode();
        CommissionStatus expected = type == PaymentTargetType.COMMISSION_INITIAL
                ? CommissionStatus.PENDING_PAYMENT
                : CommissionStatus.AWAITING_EXTRA_PAYMENT;
        if (commission.getStatus() != expected || activeCode == null || !activeCode.equalsIgnoreCase(paymentCode)) {
            log.debug("Commission payment expiry ignored: checkout no longer active [commissionId={}] [paymentCode={}]"
                            + " [activePaymentCode={}] [status={}]",
                    commission.getCommissionId(), paymentCode, activeCode, commission.getStatus());
            return Outcome.IGNORED;
        }

        if (type == PaymentTargetType.COMMISSION_INITIAL) {
            commission.expireQuote();
            commissions.save(commission);
            log.info("Commission quote expired [commissionId={}] [paymentCode={}] [quoteExpiresAt={}]",
                    commission.getCommissionId(), paymentCode, commission.getQuoteExpiresAt());
            return Outcome.QUOTE_EXPIRED;
        }

        reverter.revert(commission, activeCode, RevisionCheckoutReverter.Reason.EXPIRED, Instant.now());
        log.info("Revision checkout expired, brief reopened [commissionId={}] [paymentCode={}] [autoCompleteAt={}]",
                commission.getCommissionId(), paymentCode, commission.getAutoCompleteAt());
        return Outcome.REVISION_CHECKOUT_REVERTED;
    }
}
