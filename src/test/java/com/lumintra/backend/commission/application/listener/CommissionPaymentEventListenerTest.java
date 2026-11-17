package com.lumintra.backend.commission.application.listener;

import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.payment.domain.event.PaymentSucceededEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CMS-10: CommissionPaymentEventListener Unit Tests")
class CommissionPaymentEventListenerTest {

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private CommissionMessageRepository commissionMessageRepository;

    @InjectMocks
    private CommissionPaymentEventListener eventListener;

    private CommissionRequestEntity sampleCommission;

    @BeforeEach
    void setUp() {
        sampleCommission = CommissionRequestEntity.builder()
                .commissionId(101L)
                .customerId(501L)
                .title("Neon Cyber Samurai")
                .status(CommissionStatus.PENDING_PAYMENT)
                .activePaymentCode("CMS-101-ABCD")
                .initialPrice(BigDecimal.valueOf(500000))
                .totalPrice(BigDecimal.valueOf(500000))
                .build();
    }

    @Test
    @DisplayName("UTCID01 [N]: On valid COMMISSION_INITIAL payment, clears activePaymentCode, transitions to PAID, and inserts SYSTEM message")
    void onPaymentSucceeded_WhenValidEventAndPendingPayment_TransitionsToPaidClearsCodeAndInsertsSystemMessage() {
        // Given
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                1L,
                "CMS-101-ABCD",
                PaymentTargetType.COMMISSION_INITIAL,
                101L,
                501L,
                BigDecimal.valueOf(500000),
                99999L,
                "REF-101",
                Instant.now()
        );

        when(commissionRequestRepository.findById(101L)).thenReturn(Optional.of(sampleCommission));

        // When
        eventListener.onPaymentSucceeded(event);

        // Then
        assertThat(sampleCommission.getActivePaymentCode()).isNull();
        assertThat(sampleCommission.getStatus()).isEqualTo(CommissionStatus.PAID);
        verify(commissionRequestRepository).save(sampleCommission);

        ArgumentCaptor<CommissionMessageEntity> messageCaptor = ArgumentCaptor.forClass(CommissionMessageEntity.class);
        verify(commissionMessageRepository).save(messageCaptor.capture());

        CommissionMessageEntity capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getCommissionId()).isEqualTo(101L);
        assertThat(capturedMessage.getSenderId()).isNull();
        assertThat(capturedMessage.getSenderRole()).isEqualTo(CommissionMessageSenderRole.SYSTEM);
        assertThat(capturedMessage.getMessageType()).isEqualTo(CommissionMessageType.SYSTEM);
        assertThat(capturedMessage.getContent()).contains("500000 VND");
        assertThat(capturedMessage.getContent()).contains("unlocked");
    }

    @Test
    @DisplayName("UTCID02 [B]: On non-COMMISSION_INITIAL targetType (e.g. ORDER), ignores event and does not access repositories")
    void onPaymentSucceeded_WhenTargetTypeNotCommissionInitial_IgnoresEvent() {
        // Given
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                2L,
                "ORD-202-XYZ",
                PaymentTargetType.ORDER,
                202L,
                501L,
                BigDecimal.valueOf(300000),
                99998L,
                "REF-202",
                Instant.now()
        );

        // When
        eventListener.onPaymentSucceeded(event);

        // Then
        verifyNoInteractions(commissionRequestRepository);
        verifyNoInteractions(commissionMessageRepository);
    }

    @Test
    @DisplayName("UTCID03 [B]: On null event or null targetId, handles gracefully without database operations")
    void onPaymentSucceeded_WhenEventNullOrTargetIdNull_IgnoresGracefully() {
        // When null event
        eventListener.onPaymentSucceeded(null);

        // Then
        verifyNoInteractions(commissionRequestRepository);
        verifyNoInteractions(commissionMessageRepository);

        // Given event with null targetId
        PaymentSucceededEvent eventWithNullTarget = new PaymentSucceededEvent(
                3L,
                "CMS-NULL",
                PaymentTargetType.COMMISSION_INITIAL,
                null,
                501L,
                BigDecimal.valueOf(100000),
                99997L,
                "REF-NULL",
                Instant.now()
        );

        // When
        eventListener.onPaymentSucceeded(eventWithNullTarget);

        // Then
        verifyNoInteractions(commissionRequestRepository);
        verifyNoInteractions(commissionMessageRepository);
    }

    @Test
    @DisplayName("UTCID04 [A]: When commission not found by targetId, logs warning and does not throw exception")
    void onPaymentSucceeded_WhenCommissionNotFound_LogsWarningAndDoesNotThrow() {
        // Given
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                4L,
                "CMS-999-ABCD",
                PaymentTargetType.COMMISSION_INITIAL,
                999L,
                501L,
                BigDecimal.valueOf(500000),
                99996L,
                "REF-999",
                Instant.now()
        );

        when(commissionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        eventListener.onPaymentSucceeded(event);

        // Then
        verify(commissionRequestRepository).findById(999L);
        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionMessageRepository);
    }

    @Test
    @DisplayName("UTCID05 [B]: Idempotency: When commission is already in PAID status, skips processing and does not duplicate message")
    void onPaymentSucceeded_WhenCommissionAlreadyPaid_SkipsTransitionAndDoesNotInsertDuplicateMessage() {
        // Given
        sampleCommission.setStatus(CommissionStatus.PAID);
        sampleCommission.setActivePaymentCode(null);

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                5L,
                "CMS-101-ABCD",
                PaymentTargetType.COMMISSION_INITIAL,
                101L,
                501L,
                BigDecimal.valueOf(500000),
                99995L,
                "REF-101",
                Instant.now()
        );

        when(commissionRequestRepository.findById(101L)).thenReturn(Optional.of(sampleCommission));

        // When
        eventListener.onPaymentSucceeded(event);

        // Then
        verify(commissionRequestRepository).findById(101L);
        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionMessageRepository);
    }

    @Test
    @DisplayName("UTCID06 [N]: When event amount is null, fallback message content is used")
    void onPaymentSucceeded_WhenAmountNull_InsertsSystemMessageWithFallbackText() {
        // Given
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                6L,
                "CMS-101-ABCD",
                PaymentTargetType.COMMISSION_INITIAL,
                101L,
                501L,
                null,
                99994L,
                "REF-101",
                Instant.now()
        );

        when(commissionRequestRepository.findById(101L)).thenReturn(Optional.of(sampleCommission));

        // When
        eventListener.onPaymentSucceeded(event);

        // Then
        ArgumentCaptor<CommissionMessageEntity> messageCaptor = ArgumentCaptor.forClass(CommissionMessageEntity.class);
        verify(commissionMessageRepository).save(messageCaptor.capture());

        CommissionMessageEntity capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getContent()).contains("the required deposit");
        assertThat(capturedMessage.getContent()).contains("unlocked");
    }
}
