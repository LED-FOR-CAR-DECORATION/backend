package com.lumintra.backend.commission.application.listener;

import com.lumintra.backend.commission.application.service.CommissionPaymentExpiryHandler;
import com.lumintra.backend.payment.domain.event.PaymentExpiredEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommissionPaymentExpiredEventListenerTest {

    private final CommissionPaymentExpiryHandler handler = mock(CommissionPaymentExpiryHandler.class);
    private final CommissionPaymentExpiredEventListener listener = new CommissionPaymentExpiredEventListener(handler);
    private final PaymentExpiredEvent event =
            new PaymentExpiredEvent(77L, "LMTREV105ABC", PaymentTargetType.COMMISSION_REVISION, 105L, Instant.now());

    @Test
    @DisplayName("[N]: delegates each expired payment to the handler")
    void delegatesToHandler() {
        listener.onPaymentExpired(event);

        verify(handler).handle(event);
    }

    @Test
    @DisplayName("AC04 [A]: a failing commission is logged, never thrown back into the expiry batch")
    void swallowsHandlerFailure() {
        when(handler.handle(event)).thenThrow(new IllegalStateException("card mismatch"));

        assertThatCode(() -> listener.onPaymentExpired(event)).doesNotThrowAnyException();
    }
}
