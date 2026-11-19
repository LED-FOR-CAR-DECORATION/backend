package com.lumintra.backend.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lumintra.backend.payment.application.CreatePaymentSessionUseCase;
import com.lumintra.backend.payment.domain.model.CancelPaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.shared.exception.payment.PaymentSessionNotCancellableException;
import com.lumintra.backend.shared.exception.payment.PaymentSessionNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CancelPaymentSessionTest {
    static final String CODE = "LMTREV105ABC";
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock PaymentSessionMetadataStore metadataStore;
    @InjectMocks CreatePaymentSessionUseCase useCase;
    PaymentTransactionEntity payment;
    CancelPaymentSessionCommand command =
            new CancelPaymentSessionCommand(
                    CODE, PaymentTargetType.COMMISSION_REVISION, 105L, 501L);

    @BeforeEach
    void setup() {
        payment =
                PaymentTransactionEntity.of(
                        900L,
                        105L,
                        "SEPAY",
                        CODE,
                        new BigDecimal("35000"),
                        PaymentStatus.PENDING,
                        null,
                        Instant.now());
    }

    void found() {
        when(paymentTransactionRepository
                        .findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(CODE))
                .thenReturn(Optional.of(payment));
    }

    @Test
    void atomicallyExpiresPendingRevisionPayment() {
        found();
        when(paymentTransactionRepository.markAsExpiredIfPending(900L, "EXPIRED")).thenReturn(1);
        when(metadataStore.findByPaymentCode(CODE))
                .thenReturn(
                        Optional.of(
                                new PaymentSessionMetadata(
                                        CODE,
                                        PaymentTargetType.COMMISSION_REVISION,
                                        105L,
                                        501L,
                                        null)));
        useCase.cancelSession(
                new CancelPaymentSessionCommand(
                        " " + CODE.toLowerCase() + " ",
                        PaymentTargetType.COMMISSION_REVISION,
                        105L,
                        501L));
        verify(paymentTransactionRepository).markAsExpiredIfPending(900L, "EXPIRED");
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void permitsExpiredSessionCleanupWhenMetadataHasExpired() {
        found();
        payment.setPaymentStatus(PaymentStatus.EXPIRED);
        useCase.cancelSession(command);
        verify(paymentTransactionRepository, times(2))
                .findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(CODE);
    }

    @Test
    void rejectsAlreadyFinalizedPayments() {
        found();
        for (var status :
                new PaymentStatus[] {
                    PaymentStatus.SUCCEEDED,
                    PaymentStatus.REFUNDED,
                    PaymentStatus.FAILED,
                    PaymentStatus.CANCELLED
                }) {
            payment.setPaymentStatus(status);
            assertThatThrownBy(() -> useCase.cancelSession(command))
                    .isInstanceOf(PaymentSessionNotCancellableException.class);
        }
        verify(paymentTransactionRepository, never()).markAsExpiredIfPending(any(), any());
    }

    @Test
    void rejectsPaymentSuccessWinningTheRace() {
        var succeeded =
                PaymentTransactionEntity.of(
                        900L,
                        105L,
                        "SEPAY",
                        CODE,
                        new BigDecimal("35000"),
                        PaymentStatus.SUCCEEDED,
                        Instant.now(),
                        Instant.now());
        when(paymentTransactionRepository
                        .findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(CODE))
                .thenReturn(Optional.of(payment), Optional.of(succeeded));
        assertThatThrownBy(() -> useCase.cancelSession(command))
                .isInstanceOf(PaymentSessionNotCancellableException.class);
        verify(paymentTransactionRepository).markAsExpiredIfPending(900L, "EXPIRED");
    }

    @Test
    void rejectsMissingPayment() {
        assertThatThrownBy(() -> useCase.cancelSession(command))
                .isInstanceOf(PaymentSessionNotFoundException.class);
    }

    @Test
    void rejectsWrongTargetOrOwnerMetadata() {
        found();
        for (var metadata :
                new PaymentSessionMetadata[] {
                    new PaymentSessionMetadata(CODE, PaymentTargetType.ORDER, 105L, 501L, null),
                    new PaymentSessionMetadata(
                            CODE, PaymentTargetType.COMMISSION_REVISION, 106L, 501L, null),
                    new PaymentSessionMetadata(
                            CODE, PaymentTargetType.COMMISSION_REVISION, 105L, 502L, null)
                }) {
            when(metadataStore.findByPaymentCode(CODE)).thenReturn(Optional.of(metadata));
            assertThatThrownBy(() -> useCase.cancelSession(command))
                    .isInstanceOf(PaymentSessionNotCancellableException.class);
        }
        verify(paymentTransactionRepository, never()).markAsExpiredIfPending(any(), any());
    }

    @Test
    void rejectsWrongDatabaseTargetWithoutCachedMetadata() {
        found();
        payment.setOrderId(106L);
        assertThatThrownBy(() -> useCase.cancelSession(command))
                .isInstanceOf(PaymentSessionNotCancellableException.class);
    }

    @Test
    void validatesInternalCancellationCommand() {
        for (var invalid :
                new CancelPaymentSessionCommand[] {
                    null,
                    new CancelPaymentSessionCommand(
                            " ", PaymentTargetType.COMMISSION_REVISION, 105L, 501L),
                    new CancelPaymentSessionCommand(CODE, PaymentTargetType.ORDER, 105L, 501L),
                    new CancelPaymentSessionCommand(
                            CODE, PaymentTargetType.COMMISSION_REVISION, 0L, 501L),
                    new CancelPaymentSessionCommand(
                            CODE, PaymentTargetType.COMMISSION_REVISION, 105L, null)
                }) {
            assertThatThrownBy(() -> useCase.cancelSession(invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(paymentTransactionRepository, metadataStore);
    }
}
