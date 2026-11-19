package com.lumintra.backend.commission;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.application.ConfirmRevisionCardUseCase;
import com.lumintra.backend.commission.domain.model.*;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.*;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.*;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.*;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import com.lumintra.backend.shared.exception.commission.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ConfirmRevisionCardUseCaseTest {
    @Mock CommissionRequestRepository commissions;
    @Mock CommissionMessageRepository messages;
    @Mock PaymentSessionPort payments;
    @Mock CommissionChatBroadcastPort broadcast;
    @Spy CommissionMessageMapper mapper = new CommissionMessageMapper();
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks ConfirmRevisionCardUseCase useCase;
    CommissionRequestEntity commission;
    CommissionMessageEntity card;

    @BeforeEach
    void setup() {
        commission =
                CommissionRequestEntity.builder()
                        .commissionId(105L)
                        .customerId(501L)
                        .initialPrice(new BigDecimal("350000.00"))
                        .totalPrice(new BigDecimal("350000.00"))
                        .maxRevisions(2)
                        .status(CommissionStatus.PREVIEW_DELIVERED)
                        .autoCompleteAt(Instant.now().plusSeconds(3600))
                        .build();
        card =
                CommissionMessageEntity.create(
                        105L,
                        3L,
                        CommissionMessageSenderRole.STAFF,
                        CommissionMessageType.EXTRA_FEE_CARD,
                        "Brighter outline",
                        null,
                        "{\"brief\":\"Brighter outline\",\"revisionsUsed\":0}");
        card.setMessageId(402L);
        when(commissions.findForRevisionConfirmation(105L)).thenReturn(Optional.of(commission));
    }

    private void availableCard() {
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                        105L, CommissionMessageType.EXTRA_FEE_CARD))
                .thenReturn(Optional.of(card));
    }

    @Test
    void confirmsFreeRevisionAndBroadcastsUpdatedCard() throws Exception {
        availableCard();
        when(messages.save(card)).thenReturn(card);
        var response = useCase.execute(105L, 501L, 402L);
        assertThat(response.status()).isEqualTo(CommissionStatus.CUSTOMER_REVISION_REQUESTED);
        assertThat(response.revisionsUsed()).isEqualTo(1);
        assertThat(response.paymentSession()).isNull();
        assertThat(commission.getAutoCompleteAt()).isNull();
        assertThat(json.readTree(card.getMetadataJson()).path("status").asText())
                .isEqualTo("CONFIRMED");
        assertThat(json.readTree(card.getMetadataJson()).path("brief").asText())
                .isEqualTo("Brighter outline");
        verify(commissions).save(commission);
        verify(broadcast).broadcastMessage(eq(105L), any());
        verifyNoInteractions(payments);
    }

    @Test
    void consumesLastFreeRevisionAtBoundary() {
        availableCard();
        when(messages.save(card)).thenReturn(card);
        commission.setRevisionsUsed(1);
        assertThat(useCase.execute(105L, 501L, 402L).revisionsUsed()).isEqualTo(2);
        verifyNoInteractions(payments);
    }

    @Test
    void createsPaidSessionAtQuotaBoundaryAndAccumulatesFees() {
        availableCard();
        when(messages.save(card)).thenReturn(card);
        commission.setRevisionsUsed(2);
        commission.setExtraRevisionFee(new BigDecimal("35000.00"));
        var session =
                PaymentSessionResponse.builder()
                        .paymentCode("LUMREV105ABC")
                        .targetType(PaymentTargetType.COMMISSION_REVISION)
                        .amount(new BigDecimal("35000.00"))
                        .build();
        when(payments.createSession(any())).thenReturn(session);
        var response = useCase.execute(105L, 501L, 402L);
        var captor = ArgumentCaptor.forClass(CreatePaymentSessionCommand.class);
        verify(payments).createSession(captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(PaymentTargetType.COMMISSION_REVISION);
        assertThat(captor.getValue().targetId()).isEqualTo(105L);
        assertThat(captor.getValue().customerId()).isEqualTo(501L);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("35000");
        assertThat(response.extraRevisionFee()).isEqualByComparingTo("70000");
        assertThat(commission.getTotalPrice()).isEqualByComparingTo("420000");
        assertThat(commission.getActivePaymentCode()).isEqualTo(session.paymentCode());
        assertThat(response.status()).isEqualTo(CommissionStatus.AWAITING_EXTRA_PAYMENT);
        assertThat(response.paymentSession()).isSameAs(session);
        assertThat(response.revisionsUsed()).isEqualTo(2);
        assertThat(commission.getAutoCompleteAt()).isNull();
    }

    @Test
    void roundsPaidFeeAboveQuota() {
        availableCard();
        when(messages.save(card)).thenReturn(card);
        commission.setRevisionsUsed(3);
        commission.setInitialPrice(new BigDecimal("100.05"));
        when(payments.createSession(any()))
                .thenReturn(PaymentSessionResponse.builder().paymentCode("REV").build());
        assertThat(useCase.execute(105L, 501L, 402L).extraRevisionFee())
                .isEqualByComparingTo("10.01");
    }

    @Test
    void rejectsMissingCommission() {
        when(commissions.findForRevisionConfirmation(105L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L))
                .isInstanceOf(CommissionNotFoundException.class);
        verifyNoInteractions(messages, payments, broadcast);
    }

    @Test
    void rejectsOtherCustomerBeforeReadingChat() {
        assertThatThrownBy(() -> useCase.execute(105L, 502L, 402L))
                .isInstanceOf(CommissionOwnershipMismatchException.class);
        verifyNoInteractions(messages, payments, broadcast);
    }

    @Test
    void rejectsWrongStateAndDuplicateConfirmation() {
        availableCard();
        when(messages.save(card)).thenReturn(card);
        useCase.execute(105L, 501L, 402L);
        assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verify(messages, times(1)).save(any());
        verifyNoInteractions(payments);
    }

    @Test
    void rejectsMissingCard() {
        assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verifyNoInteractions(payments, broadcast);
    }

    @Test
    void rejectsDifferentOrOlderPreviewCard() {
        availableCard();
        assertThatThrownBy(() -> useCase.execute(105L, 501L, 401L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        var preview = CommissionMessageEntity.builder().messageId(403L).build();
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                        105L, CommissionMessageType.DELIVERABLE_CARD))
                .thenReturn(Optional.of(preview));
        assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verify(messages, never()).save(any());
        verifyNoInteractions(payments, broadcast);
    }

    @Test
    void rejectsMalformedAndConfirmedMetadata() {
        availableCard();
        for (String payload : new String[] {"not-json", "[]", "{\"status\":\"CONFIRMED\"}"}) {
            card.setMetadataJson(payload);
            assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L))
                    .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        }
        verify(messages, never()).save(any());
        verifyNoInteractions(payments, broadcast);
    }

    @Test
    void leavesCommissionAndCardUnchangedWhenPaymentFails() {
        availableCard();
        commission.setRevisionsUsed(2);
        var before = card.getMetadataJson();
        when(payments.createSession(any()))
                .thenThrow(new IllegalStateException("Payment unavailable"));
        assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(commission.getExtraRevisionFee()).isEqualByComparingTo("0");
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        assertThat(commission.getActivePaymentCode()).isNull();
        assertThat(card.getMetadataJson()).isEqualTo(before);
        verify(commissions, never()).save(any());
        verify(messages, never()).save(any());
        verifyNoInteractions(broadcast);
    }

    @Test
    void rejectsPaidRetryWithoutCreatingAnotherSession() {
        availableCard();
        when(messages.save(card)).thenReturn(card);
        commission.setRevisionsUsed(2);
        when(payments.createSession(any()))
                .thenReturn(PaymentSessionResponse.builder().paymentCode("REV").build());
        useCase.execute(105L, 501L, 402L);
        assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verify(payments, times(1)).createSession(any());
        assertThat(commission.getExtraRevisionFee()).isEqualByComparingTo("35000");
    }

    @Test
    void rejectsZeroPaidFee() {
        availableCard();
        commission.setRevisionsUsed(2);
        commission.setInitialPrice(BigDecimal.ZERO);
        assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verifyNoInteractions(payments, broadcast);
    }
}
