package com.lumintra.backend.payment;

import com.lumintra.backend.payment.domain.event.PaymentExpiredEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentExpiredEventTest {

    @Test
    @DisplayName("EVENT-01 [N]: Canonical constructor sets all fields correctly and exposes paymentId alias")
    void canonicalConstructor_setsAllFieldsCorrectly() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");
        PaymentExpiredEvent event = new PaymentExpiredEvent(
                101L,
                "LMTORD101TEST1234",
                PaymentTargetType.ORDER,
                55L,
                now
        );

        assertThat(event.transactionId()).isEqualTo(101L);
        assertThat(event.paymentId()).isEqualTo(101L);
        assertThat(event.paymentCode()).isEqualTo("LMTORD101TEST1234");
        assertThat(event.targetType()).isEqualTo(PaymentTargetType.ORDER);
        assertThat(event.targetId()).isEqualTo(55L);
        assertThat(event.expiredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("EVENT-02 [N]: Convenience 3-arg constructor defaults paymentCode to null and sets current expiredAt")
    void convenienceConstructor_defaultsPaymentCodeAndExpiredAt() {
        Instant before = Instant.now().minusSeconds(1);
        PaymentExpiredEvent event = new PaymentExpiredEvent(
                202L,
                PaymentTargetType.COMMISSION_INITIAL,
                77L
        );
        Instant after = Instant.now().plusSeconds(1);

        assertThat(event.transactionId()).isEqualTo(202L);
        assertThat(event.paymentId()).isEqualTo(202L);
        assertThat(event.paymentCode()).isNull();
        assertThat(event.targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(event.targetId()).isEqualTo(77L);
        assertThat(event.expiredAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("EVENT-03 [B]: Record equality and hashCode conform to value semantics")
    void equalityAndHashCode_conformToValueSemantics() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");
        PaymentExpiredEvent event1 = new PaymentExpiredEvent(101L, "CODE1", PaymentTargetType.ORDER, 55L, now);
        PaymentExpiredEvent event2 = new PaymentExpiredEvent(101L, "CODE1", PaymentTargetType.ORDER, 55L, now);
        PaymentExpiredEvent event3 = new PaymentExpiredEvent(102L, "CODE2", PaymentTargetType.COMMISSION_INITIAL, 56L, now);

        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
        assertThat(event1).isNotEqualTo(event3);
    }
}
