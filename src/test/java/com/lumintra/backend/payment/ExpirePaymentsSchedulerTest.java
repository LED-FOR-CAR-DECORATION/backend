package com.lumintra.backend.payment;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.payment.application.ExpirePaymentsScheduler;
import com.lumintra.backend.payment.domain.event.PaymentExpiredEvent;
import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpirePaymentsSchedulerTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentSessionMetadataStore metadataStore;
    @Mock
    private OrderPaymentLookupService orderPaymentLookupService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ExpirePaymentsScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExpirePaymentsScheduler(
                paymentTransactionRepository,
                metadataStore,
                orderPaymentLookupService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("PAY-03 [N]: Expires PENDING transaction with expired metadata and publishes PaymentExpiredEvent")
    void expirePendingPaymentsAt_expiredMetadata_marksExpiredAndPublishesEvent() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");
        Instant createdAt = now.minus(Duration.ofMinutes(20));
        Instant expiresAt = now.minus(Duration.ofMinutes(5));

        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                10L,
                501L,
                "SEPAY",
                "LMTCMS501ABCD1234",
                new BigDecimal("500000.00"),
                PaymentStatus.PENDING,
                null,
                createdAt
        );

        when(paymentTransactionRepository.findAllPendingTransactions()).thenReturn(List.of(tx));
        when(metadataStore.findByPaymentCode("LMTCMS501ABCD1234")).thenReturn(Optional.of(new PaymentSessionMetadata(
                "LMTCMS501ABCD1234",
                PaymentTargetType.COMMISSION_INITIAL,
                501L,
                42L,
                expiresAt
        )));
        when(paymentTransactionRepository.markAsExpiredIfPending(10L, PaymentTransactionEntity.DB_STATUS_EXPIRED))
                .thenReturn(1);

        int expiredCount = scheduler.expirePendingPaymentsAt(now);

        assertThat(expiredCount).isEqualTo(1);
        verify(paymentTransactionRepository).markAsExpiredIfPending(10L, PaymentTransactionEntity.DB_STATUS_EXPIRED);

        ArgumentCaptor<PaymentExpiredEvent> eventCaptor = ArgumentCaptor.forClass(PaymentExpiredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PaymentExpiredEvent event = eventCaptor.getValue();
        assertThat(event.transactionId()).isEqualTo(10L);
        assertThat(event.paymentId()).isEqualTo(10L);
        assertThat(event.paymentCode()).isEqualTo("LMTCMS501ABCD1234");
        assertThat(event.targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(event.targetId()).isEqualTo(501L);
        assertThat(event.expiredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("PAY-03 [N]: Falls back to OrderPaymentLookupService expiresAt when metadata is missing for ORDER")
    void expirePendingPaymentsAt_missingMetadataOrder_usesOrderExpiresAt() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");
        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                20L,
                102L,
                "SEPAY",
                "LMTORD102ABCD1234",
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                now.minus(Duration.ofMinutes(30))
        );

        when(paymentTransactionRepository.findAllPendingTransactions()).thenReturn(List.of(tx));
        when(metadataStore.findByPaymentCode("LMTORD102ABCD1234")).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(102L)).thenReturn(Optional.of(new OrderPaymentSnapshot(
                102L,
                77L,
                new BigDecimal("250000.00"),
                OrderStatus.PENDING_PAYMENT,
                now.minusSeconds(1)
        )));
        when(paymentTransactionRepository.markAsExpiredIfPending(20L, PaymentTransactionEntity.DB_STATUS_EXPIRED))
                .thenReturn(1);

        int expiredCount = scheduler.expirePendingPaymentsAt(now);

        assertThat(expiredCount).isEqualTo(1);

        ArgumentCaptor<PaymentExpiredEvent> eventCaptor = ArgumentCaptor.forClass(PaymentExpiredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().transactionId()).isEqualTo(20L);
        assertThat(eventCaptor.getValue().targetType()).isEqualTo(PaymentTargetType.ORDER);
        assertThat(eventCaptor.getValue().targetId()).isEqualTo(102L);
    }

    @Test
    @DisplayName("PAY-03 [N]: Uses createdAt + 24h TTL fallback for commission transaction when metadata cache is empty")
    void expirePendingPaymentsAt_missingMetadataCommission_fallsBackToCreatedAtPlus24h() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");
        PaymentTransactionEntity expiredCommission = PaymentTransactionEntity.of(
                30L,
                801L,
                "SEPAY",
                "LMTREV801XYZ12345",
                new BigDecimal("100000.00"),
                PaymentStatus.PENDING,
                null,
                now.minus(Duration.ofHours(25))
        );

        when(paymentTransactionRepository.findAllPendingTransactions()).thenReturn(List.of(expiredCommission));
        when(metadataStore.findByPaymentCode("LMTREV801XYZ12345")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.markAsExpiredIfPending(30L, PaymentTransactionEntity.DB_STATUS_EXPIRED))
                .thenReturn(1);

        int expiredCount = scheduler.expirePendingPaymentsAt(now);

        assertThat(expiredCount).isEqualTo(1);
        ArgumentCaptor<PaymentExpiredEvent> eventCaptor = ArgumentCaptor.forClass(PaymentExpiredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().transactionId()).isEqualTo(30L);
        assertThat(eventCaptor.getValue().targetType()).isEqualTo(PaymentTargetType.COMMISSION_REVISION);
        assertThat(eventCaptor.getValue().targetId()).isEqualTo(801L);
    }

    @Test
    @DisplayName("PAY-03 [B]: Leaves PENDING transactions untouched when expiresAt is equal to or after now")
    void expirePendingPaymentsAt_notYetExpired_doesNotUpdateOrPublish() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");
        PaymentTransactionEntity exactBoundary = PaymentTransactionEntity.of(
                40L,
                104L,
                "SEPAY",
                "LMTORD104EXACT001",
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                now.minus(Duration.ofMinutes(15))
        );
        PaymentTransactionEntity futureTx = PaymentTransactionEntity.of(
                41L,
                105L,
                "SEPAY",
                "LMTORD105FUTURE01",
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                now.minus(Duration.ofMinutes(5))
        );

        when(paymentTransactionRepository.findAllPendingTransactions()).thenReturn(List.of(exactBoundary, futureTx));
        when(metadataStore.findByPaymentCode("LMTORD104EXACT001")).thenReturn(Optional.of(new PaymentSessionMetadata(
                "LMTORD104EXACT001", PaymentTargetType.ORDER, 104L, 10L, now
        )));
        when(metadataStore.findByPaymentCode("LMTORD105FUTURE01")).thenReturn(Optional.of(new PaymentSessionMetadata(
                "LMTORD105FUTURE01", PaymentTargetType.ORDER, 105L, 10L, now.plus(Duration.ofMinutes(10))
        )));

        int expiredCount = scheduler.expirePendingPaymentsAt(now);

        assertThat(expiredCount).isZero();
        verify(paymentTransactionRepository, never()).markAsExpiredIfPending(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("PAY-03 [B]: Returns 0 with no updates or events when no PENDING transactions exist")
    void expirePendingPaymentsAt_emptyPendingList_returnsZero() {
        when(paymentTransactionRepository.findAllPendingTransactions()).thenReturn(List.of());

        int expiredCount = scheduler.expirePendingPaymentsAt(Instant.parse("2026-11-14T03:00:00Z"));

        assertThat(expiredCount).isZero();
        verify(paymentTransactionRepository, never()).markAsExpiredIfPending(any(), any());
        verifyNoInteractions(metadataStore, orderPaymentLookupService, eventPublisher);
    }

    @Test
    @DisplayName("PAY-03 [A]: Skips event publishing when concurrent update already transitioned row out of PENDING")
    void expirePendingPaymentsAt_concurrentUpdateReturnsZero_skipsEventPublishing() {
        Instant now = Instant.parse("2026-11-14T03:00:00Z");
        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                50L,
                106L,
                "SEPAY",
                "LMTORD106RACE0001",
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                now.minus(Duration.ofMinutes(20))
        );

        when(paymentTransactionRepository.findAllPendingTransactions()).thenReturn(List.of(tx));
        when(metadataStore.findByPaymentCode("LMTORD106RACE0001")).thenReturn(Optional.of(new PaymentSessionMetadata(
                "LMTORD106RACE0001",
                PaymentTargetType.ORDER,
                106L,
                10L,
                now.minus(Duration.ofMinutes(5))
        )));
        when(paymentTransactionRepository.markAsExpiredIfPending(eq(50L), eq(PaymentTransactionEntity.DB_STATUS_EXPIRED)))
                .thenReturn(0);

        int expiredCount = scheduler.expirePendingPaymentsAt(now);

        assertThat(expiredCount).isZero();
        verifyNoInteractions(eventPublisher);
    }
}
