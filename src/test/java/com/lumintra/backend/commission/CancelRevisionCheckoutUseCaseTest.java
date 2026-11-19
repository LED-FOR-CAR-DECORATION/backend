package com.lumintra.backend.commission;

import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.application.CancelRevisionCheckoutUseCase;
import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
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
import com.lumintra.backend.shared.exception.payment.PaymentSessionNotCancellableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CancelRevisionCheckoutUseCaseTest {

    @Test
    void cancelThenReconfirmChargesOnceAndAcceptsNewRevisionPayment() throws Exception {
        ready();
        when(messages.save(card)).thenReturn(card);
        useCase.execute(105L, 501L);
        when(commissions.findForRevisionConfirmation(105L)).thenReturn(Optional.of(commission));
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                        105L, CommissionMessageType.EXTRA_FEE_CARD))
                .thenReturn(Optional.of(card));
        when(payments.createSession(any()))
                .thenReturn(
                        com.lumintra.backend.payment.api.response.PaymentSessionResponse.builder()
                                .paymentCode("LMTREV105NEW")
                                .build());
        var confirm =
                new com.lumintra.backend.commission.application.ConfirmRevisionCardUseCase(
                        commissions, messages, payments, mapper, broadcast, json, revisionRequests);
        confirm.execute(105L, 501L, 402L);
        assertThat(commission.getExtraRevisionFee()).isEqualByComparingTo("70000");
        assertThat(commission.getActivePaymentCode()).isEqualTo("LMTREV105NEW");
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByCreatedAtDescMessageIdDesc(
                        105L, CommissionMessageType.EXTRA_FEE_CARD))
                .thenReturn(Optional.of(card));
        var listener =
                new com.lumintra.backend.commission.application.listener
                        .CommissionRevisionPaymentEventListener(
                        commissions, messages, mapper, broadcast, json, revisionRequests);
        listener.onPaymentSucceeded(
                new com.lumintra.backend.payment.domain.event.PaymentSucceededEvent(
                        901L,
                        "LMTREV105NEW",
                        PaymentTargetType.COMMISSION_REVISION,
                        105L,
                        501L,
                        new BigDecimal("35000"),
                        1234L,
                        "REF",
                        java.time.Instant.now()));
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.AWAITING_ESTIMATE);
        assertThat(commission.getExtraRevisionFee()).isEqualByComparingTo("70000");
        assertThat(commission.getRevisionsUsed()).isEqualTo(4);
        assertThat(json.readTree(card.getMetadataJson()).path("status").asText()).isEqualTo("PAID");
    }

    @Test
    void propagatesSerializationFailureBeforeChangingCommission() throws Exception {
        ready();
        var before = card.getMetadataJson();
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("Cannot serialize") {})
                .when(json)
                .writeValueAsString(any());
        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(commission.getActivePaymentCode()).isEqualTo(CODE);
        assertThat(card.getMetadataJson()).isEqualTo(before);
        verify(commissions, never()).save(any());
        verifyNoInteractions(broadcast);
    }

    static final String CODE = "LMTREV105ABC";
    @Mock CommissionRequestRepository commissions;
    @Mock CommissionMessageRepository messages;
    @Mock PaymentSessionPort payments;
    @Mock CommissionChatBroadcastPort broadcast;
    @Spy ObjectMapper json = new ObjectMapper();
    RevisionRequestFakes revisions = new RevisionRequestFakes();
    @Spy CommissionRevisionRequestRecorder revisionRequests = revisions.recorder;
    @Spy CommissionMessageMapper mapper = new CommissionMessageMapper();
    @InjectMocks CancelRevisionCheckoutUseCase useCase;
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
                        .build();
        card =
                CommissionMessageEntity.builder()
                        .messageId(402L)
                        .commissionId(105L)
                        .messageType(CommissionMessageType.EXTRA_FEE_CARD)
                        .metadataJson(
                                "{\"brief\":\"Brighter\",\"status\":\"CONFIRMED\",\"usesFreeQuota\":false,\"revisionFee\":35000.00}")
                        .build();
        when(commissions.findById(105L)).thenReturn(Optional.of(commission));
    }

    void ready() {
        when(commissions.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                        105L, CommissionMessageType.EXTRA_FEE_CARD))
                .thenReturn(Optional.of(card));
    }

    @Test
    void cancelsPendingCheckoutAndRemovesOnlyUnpaidFee() throws Exception {
        ready();
        when(messages.save(card)).thenReturn(card);
        useCase.execute(105L, 501L);
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        assertThat(commission.getActivePaymentCode()).isNull();
        assertThat(commission.getExtraRevisionFee()).isEqualByComparingTo("35000");
        assertThat(commission.getTotalPrice()).isEqualByComparingTo("385000");
        assertThat(commission.getRevisionsUsed()).isEqualTo(3);
        var metadata = json.readTree(card.getMetadataJson());
        assertThat(metadata.path("status").asText()).isEqualTo("PENDING");
        assertThat(metadata.has("paymentCode")).isFalse();
        assertThat(metadata.path("brief").asText()).isEqualTo("Brighter");
        var order = inOrder(payments, commissions);
        order.verify(commissions).findById(105L);
        order.verify(payments)
                .cancelSession(
                        new CancelPaymentSessionCommand(
                                CODE, PaymentTargetType.COMMISSION_REVISION, 105L, 501L));
        order.verify(commissions).findByIdForUpdate(105L);
        verify(broadcast).broadcastMessage(eq(105L), any());
    }

    @Test
    void removesFirstPendingFeeAtBoundary() {
        ready();
        when(messages.save(card)).thenReturn(card);
        commission.setExtraRevisionFee(new BigDecimal("35000"));
        useCase.execute(105L, 501L);
        assertThat(commission.getExtraRevisionFee()).isEqualByComparingTo("0");
        assertThat(commission.getTotalPrice()).isEqualByComparingTo("350000");
    }

    @Test
    void rejectsMissingCommission() {
        when(commissions.findById(105L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(CommissionNotFoundException.class);
        verifyNoInteractions(payments, messages, broadcast);
    }

    @Test
    void rejectsOtherCustomerBeforeCancellingPayment() {
        assertThatThrownBy(() -> useCase.execute(105L, 502L))
                .isInstanceOf(CommissionOwnershipMismatchException.class);
        verifyNoInteractions(payments, messages, broadcast);
    }

    @Test
    void rejectsWrongStateAndMissingActiveCode() {
        for (var status : CommissionStatus.values()) {
            if (status == CommissionStatus.AWAITING_EXTRA_PAYMENT) continue;
            commission.setStatus(status);
            assertThatThrownBy(() -> useCase.execute(105L, 501L))
                    .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        }
        commission.setStatus(CommissionStatus.AWAITING_EXTRA_PAYMENT);
        commission.setActivePaymentCode(null);
        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verifyNoInteractions(payments, messages, broadcast);
    }

    @Test
    void leavesCommissionUnchangedWhenPaymentAlreadySucceeded() {
        doThrow(new PaymentSessionNotCancellableException("Already paid"))
                .when(payments)
                .cancelSession(any());
        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(PaymentSessionNotCancellableException.class);
        assertThat(commission.getActivePaymentCode()).isEqualTo(CODE);
        verify(commissions, never()).findByIdForUpdate(any());
        verifyNoInteractions(messages, broadcast);
    }

    @Test
    void revalidatesLockedCommissionWhenCheckoutChanges() {
        var changed =
                CommissionRequestEntity.builder()
                        .customerId(501L)
                        .status(CommissionStatus.AWAITING_EXTRA_PAYMENT)
                        .activePaymentCode("NEW-SESSION")
                        .build();
        when(commissions.findByIdForUpdate(105L)).thenReturn(Optional.of(changed));
        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verify(commissions, never()).save(any());
        verifyNoInteractions(messages, broadcast);
    }

    @Test
    void rejectsMissingCard() {
        when(commissions.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verify(commissions, never()).save(any());
        verifyNoInteractions(broadcast);
    }

    @Test
    void rejectsInvalidCardAndFeeWithoutSaving() {
        ready();
        for (String payload :
                new String[] {
                    "bad-json",
                    "[]",
                    "null",
                    "{\"status\":\"PAID\",\"revisionFee\":35000}",
                    "{\"status\":\"CONFIRMED\",\"revisionFee\":0}",
                    "{\"status\":\"CONFIRMED\",\"revisionFee\":80000}",
                    "{\"status\":\"CONFIRMED\",\"revisionFee\":35000,\"usesFreeQuota\":true}",
                    "{\"status\":\"CONFIRMED\",\"revisionFee\":35000,\"paymentCode\":\"OTHER\"}"
                }) {
            card.setMetadataJson(payload);
            assertThatThrownBy(() -> useCase.execute(105L, 501L))
                    .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        }
        verify(commissions, never()).save(any());
        verify(messages, never()).save(any());
        verifyNoInteractions(broadcast);
    }

    @Test
    void rejectsDuplicateCancellationWithoutCancellingAnotherPayment() {
        ready();
        when(messages.save(card)).thenReturn(card);
        useCase.execute(105L, 501L);
        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verify(payments, times(1)).cancelSession(any());
    }

    @Test
    void cancellationReopensTheRecordedBrief() {
        ready();
        when(messages.save(card)).thenReturn(card);
        var revision =
                revisions.recorder.open(card, 4, "Brighter", false, new BigDecimal("35000.00"));
        revision.awaitPayment(4, new BigDecimal("35000.00"), CODE, java.time.Instant.now());

        useCase.execute(105L, 501L);

        assertThat(revisions.only().getStatus()).isEqualTo(CommissionRevisionRequestStatus.PENDING);
        assertThat(revisions.only().getPaymentCode()).isNull();
        assertThat(revisions.only().getConfirmedAt()).isNull();
    }
}
