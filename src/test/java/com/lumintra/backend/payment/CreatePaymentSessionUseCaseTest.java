package com.lumintra.backend.payment;

import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.application.CreatePaymentSessionUseCase;
import com.lumintra.backend.payment.domain.model.CreatePaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.model.SePayQrDetails;
import com.lumintra.backend.payment.domain.service.PaymentGatewayPort;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.payment.infrastructure.persistence.mapper.PaymentSessionMapper;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatePaymentSessionUseCaseTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @Mock
    private PaymentSessionMetadataStore metadataStore;

    private CreatePaymentSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreatePaymentSessionUseCase(
                paymentTransactionRepository,
                paymentGatewayPort,
                metadataStore,
                new PaymentSessionMapper()
        );
    }

    @Test
    @DisplayName("PAY-01 [N]: Creates PENDING SePay session for COMMISSION_INITIAL, persists entity, and caches metadata")
    void createSession_commissionInitial_persistsTransactionAndStoresMetadata() {
        String code = "LUMCMS301A1B2C3D4";
        CreatePaymentSessionCommand command = new CreatePaymentSessionCommand(
                PaymentTargetType.COMMISSION_INITIAL,
                301L,
                42L,
                new BigDecimal("450000"),
                Duration.ofHours(24)
        );
        SePayQrDetails qrDetails = new SePayQrDetails(
                code,
                "https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=450000&des=" + code + "&showinfo=true&holder=NGUYEN%20QUANG%20KHANH",
                "TPBank",
                "10006140844",
                "NGUYEN QUANG KHANH"
        );

        when(paymentGatewayPort.generatePaymentCode(PaymentTargetType.COMMISSION_INITIAL, 301L)).thenReturn(code);
        when(paymentGatewayPort.buildQrDetails(code, new BigDecimal("450000.00"))).thenReturn(qrDetails);
        when(paymentTransactionRepository.save(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity entity = invocation.getArgument(0);
            entity.setPaymentId(9001L);
            entity.setCreatedAt(Instant.parse("2026-11-12T16:45:00Z"));
            return entity;
        });

        PaymentSessionResponse response = useCase.createSession(command);

        assertThat(response.paymentId()).isEqualTo(9001L);
        assertThat(response.paymentCode()).isEqualTo(code);
        assertThat(response.targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(response.targetId()).isEqualTo(301L);
        assertThat(response.amount()).isEqualByComparingTo("450000.00");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.qrUrl()).isEqualTo(qrDetails.qrUrl());
        assertThat(response.expiresAt()).isNotNull();

        ArgumentCaptor<PaymentSessionMetadata> metadataCaptor = ArgumentCaptor.forClass(PaymentSessionMetadata.class);
        verify(metadataStore).save(metadataCaptor.capture(), any(Duration.class));
        assertThat(metadataCaptor.getValue().customerId()).isEqualTo(42L);
        assertThat(metadataCaptor.getValue().targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(metadataCaptor.getValue().targetId()).isEqualTo(301L);
    }

    @Test
    @DisplayName("PAY-01 [A]: Rejects zero or negative payment session amount")
    void createSession_nonPositiveAmount_throwsIllegalArgumentException() {
        CreatePaymentSessionCommand command = new CreatePaymentSessionCommand(
                PaymentTargetType.ORDER,
                101L,
                42L,
                BigDecimal.ZERO,
                Duration.ofMinutes(15)
        );

        assertThatThrownBy(() -> useCase.createSession(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }
}
