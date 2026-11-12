package com.lumintra.backend.commerce.infrastructure.integration;

import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentIntentResult;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.service.PaymentIntentPort;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.CreatePaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Payment gateway integration adapter that delegates order payment session creation
 * to the centralized {@link PaymentSessionPort} in the payment module.
 */
@Component
@RequiredArgsConstructor
public class SepayPaymentIntentAdapter implements PaymentIntentPort {

    private static final Duration ORDER_PAYMENT_TTL = Duration.ofMinutes(15);

    private final PaymentSessionPort paymentSessionPort;

    @Override
    public PaymentIntentResult initializePaymentIntent(Long orderId, BigDecimal amount, PaymentGateway gateway) {
        PaymentSessionResponse session = paymentSessionPort.createSession(
                new CreatePaymentSessionCommand(
                        PaymentTargetType.ORDER,
                        orderId,
                        null,
                        amount,
                        ORDER_PAYMENT_TTL
                )
        );

        return new PaymentIntentResult(
                session.paymentId(),
                gateway != null ? gateway : PaymentGateway.SEPAY,
                PaymentStatus.PENDING,
                session.paymentCode()
        );
    }
}
