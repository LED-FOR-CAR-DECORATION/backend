package com.lumintra.backend.commission.application.listener;

import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.RevisionRequestFakes;
import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.domain.event.PaymentSucceededEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;

import jakarta.persistence.LockModeType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CommissionRevisionPaymentEventListenerTest {
    private static final String CODE = "LMTREV105ABC";
    private static final Instant PAID_AT = Instant.parse("2026-11-19T03:00:00Z");
    @Mock CommissionRequestRepository commissions;
    @Mock CommissionMessageRepository messages;
    @Mock CommissionChatBroadcastPort broadcast;
    @Spy CommissionMessageMapper mapper = new CommissionMessageMapper();
    @Spy ObjectMapper json = new ObjectMapper();
    RevisionRequestFakes revisions = new RevisionRequestFakes();
    @Spy CommissionRevisionRequestRecorder revisionRequests = revisions.recorder;
    @InjectMocks CommissionRevisionPaymentEventListener listener;
    CommissionRequestEntity commission;
    CommissionMessageEntity card;

    @BeforeEach
    void setup() {
        commission =
                CommissionRequestEntity.builder()
                        .commissionId(105L)
                        .customerId(501L)
                        .status(CommissionStatus.AWAITING_EXTRA_PAYMENT)
                        .activePaymentCode(CODE)
                        .initialPrice(new BigDecimal("350000.00"))
                        .extraRevisionFee(new BigDecimal("70000.00"))
                        .totalPrice(new BigDecimal("420000.00"))
                        .revisionsUsed(3)
                        .maxRevisions(2)
                        .autoCompleteAt(PAID_AT)
                        .build();
        card =
                CommissionMessageEntity.create(
                        105L,
                        3L,
                        CommissionMessageSenderRole.STAFF,
                        CommissionMessageType.EXTRA_FEE_CARD,
                        "Brighter outline",
                        null,
                        "{\"brief\":\"Brighter"
                            + " outline\",\"status\":\"CONFIRMED\",\"usesFreeQuota\":false,\"revisionFee\":35000.00}");
        card.setMessageId(402L);
    }

    private PaymentSucceededEvent event(String code, Long customerId, BigDecimal amount) {
        return new PaymentSucceededEvent(
                900L,
                code,
                PaymentTargetType.COMMISSION_REVISION,
                105L,
                customerId,
                amount,
                1234L,
                "REF",
                PAID_AT);
    }

    private PaymentSucceededEvent validEvent() {
        return event(CODE, 501L, new BigDecimal("35000.00"));
    }

    private void awaitingCommission() {
        when(commissions.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
    }

    private void confirmedCard() {
        awaitingCommission();
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByCreatedAtDescMessageIdDesc(
                        105L, CommissionMessageType.EXTRA_FEE_CARD))
                .thenReturn(Optional.of(card));
    }

    @Test
    void marksCardPaidUnlocksRevisionAndPreservesPrices() throws Exception {
        confirmedCard();
        when(messages.save(card)).thenReturn(card);
        listener.onPaymentSucceeded(validEvent());
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.AWAITING_ESTIMATE);
        assertThat(commission.getActivePaymentCode()).isNull();
        assertThat(commission.getAutoCompleteAt()).isNull();
        assertThat(commission.getRevisionsUsed()).isEqualTo(4);
        assertThat(commission.getInitialPrice()).isEqualByComparingTo("350000");
        assertThat(commission.getExtraRevisionFee()).isEqualByComparingTo("70000");
        assertThat(commission.getTotalPrice()).isEqualByComparingTo("420000");
        var metadata = json.readTree(card.getMetadataJson());
        assertThat(metadata.path("status").asText()).isEqualTo("PAID");
        assertThat(metadata.path("brief").asText()).isEqualTo("Brighter outline");
        assertThat(metadata.path("paymentCode").asText()).isEqualTo(CODE);
        assertThat(metadata.path("paymentId").asLong()).isEqualTo(900L);
        assertThat(metadata.path("paidAt").asText()).isEqualTo(PAID_AT.toString());
        verify(commissions).save(commission);
        verify(messages).save(card);
        verify(broadcast)
                .broadcastMessage(eq(105L), argThat(r -> r.metadataJson().contains("PAID")));
    }

    @Test
    void acceptsMissingCachedCustomerWhenActivePaymentCodeMatches() {
        confirmedCard();
        when(messages.save(card)).thenReturn(card);
        listener.onPaymentSucceeded(event(CODE.toLowerCase(), null, new BigDecimal("35000")));
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.AWAITING_ESTIMATE);
    }

    @Test
    void consumesFirstPaidRevisionAtQuotaBoundaryOnlyOnce() {
        confirmedCard();
        when(messages.save(card)).thenReturn(card);
        commission.setRevisionsUsed(2);
        listener.onPaymentSucceeded(validEvent());
        listener.onPaymentSucceeded(validEvent());
        assertThat(commission.getRevisionsUsed()).isEqualTo(3);
        verify(messages, times(1)).save(card);
        verify(commissions, times(1)).save(commission);
        verify(broadcast, times(1)).broadcastMessage(eq(105L), any());
    }

    @Test
    void ignoresNullUnrelatedAndInvalidTargetEvents() {
        listener.onPaymentSucceeded(null);
        for (PaymentTargetType type :
                new PaymentTargetType[] {
                    PaymentTargetType.ORDER, PaymentTargetType.COMMISSION_INITIAL, null
                }) {
            listener.onPaymentSucceeded(
                    new PaymentSucceededEvent(
                            900L, CODE, type, 105L, 501L, BigDecimal.TEN, null, null, PAID_AT));
        }
        for (Long target : new Long[] {null, 0L, -1L}) {
            listener.onPaymentSucceeded(
                    new PaymentSucceededEvent(
                            900L,
                            CODE,
                            PaymentTargetType.COMMISSION_REVISION,
                            target,
                            501L,
                            BigDecimal.TEN,
                            null,
                            null,
                            PAID_AT));
        }
        listener.onPaymentSucceeded(event(null, 501L, BigDecimal.TEN));
        listener.onPaymentSucceeded(event(" ", 501L, BigDecimal.TEN));
        verifyNoInteractions(commissions, messages, broadcast);
    }

    @Test
    void ignoresMissingCommission() {
        listener.onPaymentSucceeded(validEvent());
        verifyNoInteractions(messages, broadcast);
    }

    @Test
    void ignoresAllOtherLifecycleStates() {
        awaitingCommission();
        for (var status : CommissionStatus.values()) {
            if (status == CommissionStatus.AWAITING_EXTRA_PAYMENT) continue;
            commission.setStatus(status);
            listener.onPaymentSucceeded(validEvent());
            assertThat(commission.getStatus()).isEqualTo(status);
        }
        verify(commissions, never()).save(any());
        verifyNoInteractions(messages, broadcast);
    }

    @Test
    void ignoresOldSessionOrMissingActiveCode() {
        awaitingCommission();
        listener.onPaymentSucceeded(event("OLD-SESSION", 501L, new BigDecimal("35000")));
        commission.setActivePaymentCode(null);
        listener.onPaymentSucceeded(validEvent());
        verifyNoInteractions(messages, broadcast);
        verify(commissions, never()).save(any());
    }

    @Test
    void rejectsDifferentCustomerForMatchingSession() {
        awaitingCommission();
        assertThatThrownBy(
                        () ->
                                listener.onPaymentSucceeded(
                                        event(CODE, 502L, new BigDecimal("35000"))))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(messages, broadcast);
    }

    @Test
    void rejectsMissingConfirmedCardWithoutUnlockingCommission() {
        awaitingCommission();
        assertThatThrownBy(() -> listener.onPaymentSucceeded(validEvent()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.AWAITING_EXTRA_PAYMENT);
        assertThat(commission.getActivePaymentCode()).isEqualTo(CODE);
        verify(commissions, never()).save(any());
        verifyNoInteractions(broadcast);
    }

    @Test
    void rejectsMalformedUnconfirmedFreeAndMismatchedCardMetadata() {
        confirmedCard();
        for (String payload :
                new String[] {
                    "not-json",
                    "null",
                    "[]",
                    "{\"status\":\"PENDING\",\"revisionFee\":35000}",
                    "{\"status\":\"PAID\",\"revisionFee\":35000}",
                    "{\"status\":\"CONFIRMED\",\"usesFreeQuota\":true,\"revisionFee\":35000}",
                    "{\"status\":\"CONFIRMED\",\"revisionFee\":0}",
                    "{\"status\":\"CONFIRMED\",\"revisionFee\":\"35000\"}",
                    "{\"status\":\"CONFIRMED\",\"revisionFee\":35000,\"paymentCode\":\"OLD\"}"
                }) {
            card.setMetadataJson(payload);
            assertThatThrownBy(() -> listener.onPaymentSucceeded(validEvent()))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(commission.getActivePaymentCode()).isEqualTo(CODE);
        }
        verify(messages, never()).save(any());
        verify(commissions, never()).save(any());
        verifyNoInteractions(broadcast);
    }

    @Test
    void rejectsWrongOrMissingPaymentAmount() {
        confirmedCard();
        for (BigDecimal amount :
                new BigDecimal[] {
                    null, BigDecimal.ZERO, new BigDecimal("34999"), new BigDecimal("35001")
                }) {
            assertThatThrownBy(() -> listener.onPaymentSucceeded(event(CODE, 501L, amount)))
                    .isInstanceOf(IllegalStateException.class);
        }
        verify(messages, never()).save(any());
        verifyNoInteractions(broadcast);
    }

    @Test
    void propagatesSerializationFailureBeforeMutatingState() throws Exception {
        confirmedCard();
        var before = card.getMetadataJson();
        doThrow(new JsonProcessingException("Cannot serialize") {})
                .when(json)
                .writeValueAsString(any());
        assertThatThrownBy(() -> listener.onPaymentSucceeded(validEvent()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.AWAITING_EXTRA_PAYMENT);
        assertThat(card.getMetadataJson()).isEqualTo(before);
        verify(commissions, never()).save(any());
        verifyNoInteractions(broadcast);
    }

    @Test
    void propagatesPersistenceFailureWithoutBroadcasting() {
        confirmedCard();
        when(messages.save(card)).thenThrow(new IllegalStateException("Database unavailable"));
        assertThatThrownBy(() -> listener.onPaymentSucceeded(validEvent()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(broadcast);
    }

    @Test
    void registersSynchronousTransactionalListenerAndWriteLock() throws Exception {
        var method =
                CommissionRevisionPaymentEventListener.class.getMethod(
                        "onPaymentSucceeded", PaymentSucceededEvent.class);
        assertThat(method.getAnnotation(EventListener.class)).isNotNull();
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
        assertThat(
                        CommissionRequestRepository.class
                                .getMethod("findByIdForUpdate", Long.class)
                                .getAnnotation(Lock.class)
                                .value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void paymentConfirmsTheRecordedBrief() {
        awaitingCommission();
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByCreatedAtDescMessageIdDesc(
                        105L, CommissionMessageType.EXTRA_FEE_CARD))
                .thenReturn(Optional.of(card));
        when(messages.save(card)).thenReturn(card);
        var revision =
                revisions.recorder.open(card, 4, "Brighter outline", false, new BigDecimal("35000.00"));
        revision.awaitPayment(4, new BigDecimal("35000.00"), CODE, PAID_AT);

        listener.onPaymentSucceeded(validEvent());

        assertThat(revisions.only().getStatus()).isEqualTo(CommissionRevisionRequestStatus.CONFIRMED);
        assertThat(revisions.only().getPaidAt()).isEqualTo(PAID_AT);
        assertThat(revisions.only().getDescription()).isEqualTo("Brighter outline");
    }
}
