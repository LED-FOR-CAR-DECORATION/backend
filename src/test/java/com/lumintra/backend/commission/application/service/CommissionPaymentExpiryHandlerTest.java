package com.lumintra.backend.commission.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.RevisionRequestFakes;
import com.lumintra.backend.commission.application.service.CommissionPaymentExpiryHandler.Outcome;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.domain.event.PaymentExpiredEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommissionPaymentExpiryHandlerTest {

    private static final long ID = 105L;
    private static final String INITIAL_CODE = "LMTCMS105ABC";
    private static final String REVISION_CODE = "LMTREV105ABC";

    private final ObjectMapper json = new ObjectMapper();
    private CommissionRequestRepository commissions;
    private CommissionMessageRepository messages;
    private CommissionChatBroadcastPort broadcast;
    private RevisionRequestFakes revisions;
    private CommissionPaymentExpiryHandler handler;

    @BeforeEach
    void setUp() {
        commissions = mock(CommissionRequestRepository.class);
        messages = mock(CommissionMessageRepository.class);
        broadcast = mock(CommissionChatBroadcastPort.class);
        revisions = new RevisionRequestFakes();
        handler = new CommissionPaymentExpiryHandler(commissions, new RevisionCheckoutReverter(
                commissions, messages, new CommissionMessageMapper(), broadcast, json, revisions.recorder));
        when(messages.save(any(CommissionMessageEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CommissionRequestEntity commission(CommissionStatus status, String activeCode) {
        CommissionRequestEntity entity = CommissionRequestEntity.builder()
                .commissionId(ID).customerId(501L).status(status).activePaymentCode(activeCode)
                .quoteExpiresAt(Instant.parse("2026-11-20T00:00:00Z"))
                .initialPrice(new BigDecimal("350000.00"))
                .extraRevisionFee(new BigDecimal("35000.00"))
                .totalPrice(new BigDecimal("385000.00"))
                .revisionsUsed(1).maxRevisions(1)
                .build();
        when(commissions.findByIdForUpdate(ID)).thenReturn(Optional.of(entity));
        return entity;
    }

    private static PaymentExpiredEvent expired(PaymentTargetType type, String code) {
        return new PaymentExpiredEvent(77L, code, type, ID, Instant.now());
    }

    // ---------------------------------------------------------------- AC01 initial quote

    @Nested
    class InitialQuote {

        @Test
        @DisplayName("AC01 [N]: PENDING_PAYMENT with the expired code becomes EXPIRED and keeps quoteExpiresAt")
        void expiresQuote() {
            CommissionRequestEntity entity = commission(CommissionStatus.PENDING_PAYMENT, INITIAL_CODE);

            assertThat(handler.handle(expired(PaymentTargetType.COMMISSION_INITIAL, INITIAL_CODE)))
                    .isEqualTo(Outcome.QUOTE_EXPIRED);

            assertThat(entity.getStatus()).isEqualTo(CommissionStatus.EXPIRED);
            assertThat(entity.getActivePaymentCode()).isNull();
            assertThat(entity.getQuoteExpiresAt()).isEqualTo(Instant.parse("2026-11-20T00:00:00Z"));
            verify(commissions).save(entity);
            verifyNoInteractions(messages, broadcast);
        }

        @Test
        @DisplayName("AC01 [B]: the payment code matches case-insensitively")
        void matchesCodeIgnoringCase() {
            CommissionRequestEntity entity = commission(CommissionStatus.PENDING_PAYMENT, INITIAL_CODE);

            assertThat(handler.handle(expired(PaymentTargetType.COMMISSION_INITIAL, INITIAL_CODE.toLowerCase())))
                    .isEqualTo(Outcome.QUOTE_EXPIRED);
            assertThat(entity.getStatus()).isEqualTo(CommissionStatus.EXPIRED);
        }

        @Test
        @DisplayName("AC04 [A]: an older payment code, or a commission already paid, is left alone")
        void ignoresStaleOrPaid() {
            CommissionRequestEntity entity = commission(CommissionStatus.PENDING_PAYMENT, "LMTCMS105NEW");
            assertThat(handler.handle(expired(PaymentTargetType.COMMISSION_INITIAL, INITIAL_CODE)))
                    .isEqualTo(Outcome.IGNORED);
            assertThat(entity.getStatus()).isEqualTo(CommissionStatus.PENDING_PAYMENT);

            entity = commission(CommissionStatus.PAID, null);
            assertThat(handler.handle(expired(PaymentTargetType.COMMISSION_INITIAL, INITIAL_CODE)))
                    .isEqualTo(Outcome.IGNORED);
            assertThat(entity.getStatus()).isEqualTo(CommissionStatus.PAID);
            verify(commissions, never()).save(any());
        }
    }

    // ---------------------------------------------------------------- AC02 / AC03 revision fee

    @Nested
    class RevisionFee {

        private CommissionMessageEntity card;

        @BeforeEach
        void card() {
            card = CommissionMessageEntity.builder()
                    .messageId(402L).commissionId(ID).messageType(CommissionMessageType.EXTRA_FEE_CARD)
                    .metadataJson("{\"brief\":\"Brighter\",\"status\":\"CONFIRMED\",\"usesFreeQuota\":false,"
                            + "\"revisionFee\":35000.00,\"paymentCode\":\"" + REVISION_CODE + "\"}")
                    .build();
            when(messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(ID, CommissionMessageType.EXTRA_FEE_CARD))
                    .thenReturn(Optional.of(card));
            var revision = revisions.recorder.open(card, 2, "Brighter", false, new BigDecimal("35000.00"));
            revision.awaitPayment(2, new BigDecimal("35000.00"), REVISION_CODE, Instant.now());
        }

        @Test
        @DisplayName("AC02 [N]: reverts to PREVIEW_DELIVERED, reopens the brief and removes the unpaid fee")
        void revertsRevisionCheckout() throws Exception {
            CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_EXTRA_PAYMENT, REVISION_CODE);

            assertThat(handler.handle(expired(PaymentTargetType.COMMISSION_REVISION, REVISION_CODE)))
                    .isEqualTo(Outcome.REVISION_CHECKOUT_REVERTED);

            assertThat(entity.getStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
            assertThat(entity.getActivePaymentCode()).isNull();
            assertThat(entity.getExtraRevisionFee()).isEqualByComparingTo("0");
            assertThat(entity.getTotalPrice()).isEqualByComparingTo("350000");
            assertThat(entity.getRevisionsUsed()).isEqualTo(1);
            var metadata = json.readTree(card.getMetadataJson());
            assertThat(metadata.path("status").asText()).isEqualTo("PENDING");
            assertThat(metadata.has("paymentCode")).isFalse();
            assertThat(metadata.path("expiredPaymentCode").asText()).isEqualTo(REVISION_CODE);
            assertThat(metadata.has("cancelledPaymentCode")).isFalse();
            assertThat(revisions.only().getStatus()).isEqualTo(CommissionRevisionRequestStatus.PENDING);
            assertThat(revisions.only().getPaymentCode()).isNull();
            verify(broadcast).broadcastMessage(eq(ID), any());
        }

        @Test
        @DisplayName("AC03 [B]: an auto-complete deadline that already passed is pushed to now + 24h")
        void pushesPassedAutoCompleteDeadline() {
            CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_EXTRA_PAYMENT, REVISION_CODE);
            entity.setAutoCompleteAt(Instant.now().minus(Duration.ofDays(1)));

            handler.handle(expired(PaymentTargetType.COMMISSION_REVISION, REVISION_CODE));

            assertThat(entity.getAutoCompleteAt())
                    .isCloseTo(Instant.now().plus(Duration.ofHours(24)), within(Duration.ofSeconds(5)));
        }

        @Test
        @DisplayName("AC03 [N]: a later auto-complete deadline is kept")
        void keepsLaterAutoCompleteDeadline() {
            CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_EXTRA_PAYMENT, REVISION_CODE);
            Instant later = Instant.now().plus(Duration.ofDays(5));
            entity.setAutoCompleteAt(later);

            handler.handle(expired(PaymentTargetType.COMMISSION_REVISION, REVISION_CODE));

            assertThat(entity.getAutoCompleteAt()).isEqualTo(later);
        }

        @Test
        @DisplayName("AC04 [A]: an expired code that is no longer the active checkout is ignored")
        void ignoresStaleRevisionCode() {
            CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_EXTRA_PAYMENT, "LMTREV105NEW");

            assertThat(handler.handle(expired(PaymentTargetType.COMMISSION_REVISION, REVISION_CODE)))
                    .isEqualTo(Outcome.IGNORED);
            assertThat(entity.getStatus()).isEqualTo(CommissionStatus.AWAITING_EXTRA_PAYMENT);
            verifyNoInteractions(broadcast);
        }

        @Test
        @DisplayName("AC04 [A]: a card that doesn't match the checkout fails without changing the commission")
        void mismatchedCardFails() {
            CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_EXTRA_PAYMENT, REVISION_CODE);
            card.setMetadataJson("{\"status\":\"PAID\",\"revisionFee\":35000}");

            assertThatThrownBy(() -> handler.handle(expired(PaymentTargetType.COMMISSION_REVISION, REVISION_CODE)))
                    .isInstanceOf(InvalidCommissionStatusTransitionException.class);
            assertThat(entity.getStatus()).isEqualTo(CommissionStatus.AWAITING_EXTRA_PAYMENT);
            verify(commissions, never()).save(any());
        }
    }

    // ---------------------------------------------------------------- AC04 / AC05

    @Test
    @DisplayName("AC05 [A]: order payments are ignored without touching commissions")
    void ignoresOrders() {
        assertThat(handler.handle(expired(PaymentTargetType.ORDER, "LMTORD1"))).isEqualTo(Outcome.IGNORED);
        verifyNoInteractions(commissions);
    }

    @Test
    @DisplayName("AC04 [A]: missing payment code, missing target, or unknown commission is ignored")
    void ignoresIncompleteEvents() {
        assertThat(handler.handle(new PaymentExpiredEvent(77L, null, PaymentTargetType.COMMISSION_INITIAL, ID, Instant.now())))
                .isEqualTo(Outcome.IGNORED);
        assertThat(handler.handle(new PaymentExpiredEvent(77L, INITIAL_CODE, PaymentTargetType.COMMISSION_INITIAL, null, Instant.now())))
                .isEqualTo(Outcome.IGNORED);
        when(commissions.findByIdForUpdate(ID)).thenReturn(Optional.empty());
        assertThat(handler.handle(expired(PaymentTargetType.COMMISSION_INITIAL, INITIAL_CODE))).isEqualTo(Outcome.IGNORED);
        verify(commissions, never()).save(any());
    }

    @Test
    @DisplayName("AC03 [B]: response deadline is max(current, now + 24h), and now + 24h when unset")
    void responseDeadlineRule() {
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        Instant floor = now.plus(Duration.ofHours(24));
        assertThat(RevisionCheckoutReverter.responseDeadline(null, now)).isEqualTo(floor);
        assertThat(RevisionCheckoutReverter.responseDeadline(now.minusSeconds(1), now)).isEqualTo(floor);
        assertThat(RevisionCheckoutReverter.responseDeadline(floor, now)).isEqualTo(floor);
        assertThat(RevisionCheckoutReverter.responseDeadline(floor.plusSeconds(1), now)).isEqualTo(floor.plusSeconds(1));
    }
}
