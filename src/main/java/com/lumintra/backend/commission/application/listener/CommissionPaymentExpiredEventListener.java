package com.lumintra.backend.commission.application.listener;

import com.lumintra.backend.commission.application.service.CommissionPaymentExpiryHandler;
import com.lumintra.backend.payment.domain.event.PaymentExpiredEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * CMS-26 (#131): reacts to {@link PaymentExpiredEvent} for commission payments.
 *
 * <p>Runs after {@code ExpirePaymentsScheduler}'s transaction commits, so the commission only changes once
 * the payment is definitely EXPIRED, and a failure here can never roll back the other expiries in the batch.
 * Each event is applied in its own transaction ({@link CommissionPaymentExpiryHandler}); failures are logged
 * and swallowed so the remaining events of the batch are still handled.
 */
@Component
@RequiredArgsConstructor
public class CommissionPaymentExpiredEventListener {

    private static final Logger log = LoggerFactory.getLogger(CommissionPaymentExpiredEventListener.class);

    private final CommissionPaymentExpiryHandler handler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentExpired(PaymentExpiredEvent event) {
        try {
            handler.handle(event);
        } catch (RuntimeException ex) {
            log.error("Commission payment expiry failed; commission left unchanged [targetType={}] [commissionId={}]"
                            + " [paymentCode={}] [reason={}]",
                    event.targetType(), event.targetId(), event.paymentCode(), ex.getMessage(), ex);
        }
    }
}
