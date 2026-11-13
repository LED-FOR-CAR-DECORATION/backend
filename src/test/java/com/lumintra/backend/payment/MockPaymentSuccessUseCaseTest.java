package com.lumintra.backend.payment;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.application.MockPaymentSuccessUseCase;
import com.lumintra.backend.payment.domain.event.PaymentSucceededEvent;
import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.model.SePayQrDetails;
import com.lumintra.backend.payment.domain.service.PaymentGatewayPort;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.payment.infrastructure.integration.SePayProperties;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.payment.infrastructure.persistence.mapper.PaymentSessionMapper;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.shared.exception.payment.PaymentSessionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockPaymentSuccessUseCaseTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentSessionMetadataStore metadataStore;

    @Mock
    private OrderPaymentLookupService orderPaymentLookupService;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentSessionMapper paymentSessionMapper;
    private SePayProperties sePayProperties;
    private MockPaymentSuccessUseCase useCase;

    @BeforeEach
    void setUp() {
        paymentSessionMapper = new PaymentSessionMapper();
        sePayProperties = new SePayProperties(
                "https://vietqr.app/img",
                "TPBank",
                "10006140844",
                "LUMINTRA",
                "LMT",
                "secret-webhook-key"
        );

        useCase = new MockPaymentSuccessUseCase(
                paymentTransactionRepository,
                metadataStore,
                orderPaymentLookupService,
                paymentGatewayPort,
                paymentSessionMapper,
                sePayProperties,
                eventPublisher
        );
    }

    @Test
    @DisplayName("MOCK-01: Successfully simulates payment for pending order transaction and publishes event")
    void execute_pendingOrderTransaction_succeedsAndPublishesEvent() {
        String paymentCode = "LMTORD102A1B2C3D4";
        PaymentTransactionEntity transaction = PaymentTransactionEntity.of(
                5001L,
                102L,
                "SEPAY",
                paymentCode,
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                Instant.now().minusSeconds(60)
        );

        PaymentSessionMetadata metadata = new PaymentSessionMetadata(
                paymentCode,
                PaymentTargetType.ORDER,
                102L,
                42L,
                Instant.now().plusSeconds(900)
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(paymentCode))
                .thenReturn(Optional.of(transaction));
        when(metadataStore.findByPaymentCode(paymentCode)).thenReturn(Optional.of(metadata));
        when(paymentTransactionRepository.markAsSucceededIfPending(eq(5001L), eq("SUCCESS"), any(Instant.class)))
                .thenReturn(1);
        when(paymentGatewayPort.buildQrDetails(any(), any())).thenReturn(
                new SePayQrDetails(paymentCode, "https://qr.url", "TPBank", "10006140844", "LUMINTRA")
        );

        PaymentSessionResponse response = useCase.execute(paymentCode);

        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(5001L);
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);

        verify(paymentTransactionRepository).markAsSucceededIfPending(eq(5001L), eq("SUCCESS"), any(Instant.class));

        ArgumentCaptor<PaymentSucceededEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        PaymentSucceededEvent event = eventCaptor.getValue();
        assertThat(event.paymentId()).isEqualTo(5001L);
        assertThat(event.paymentCode()).isEqualTo(paymentCode);
        assertThat(event.targetType()).isEqualTo(PaymentTargetType.ORDER);
        assertThat(event.targetId()).isEqualTo(102L);
        assertThat(event.customerId()).isEqualTo(42L);
        assertThat(event.amount()).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("MOCK-02: Resolves customerId from OrderPaymentLookupService if metadata is missing")
    void execute_noMetadata_resolvesCustomerIdFromOrderLookup() {
        String paymentCode = "LMTORD102A1B2C3D4";
        PaymentTransactionEntity transaction = PaymentTransactionEntity.of(
                5001L,
                102L,
                "SEPAY",
                paymentCode,
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                Instant.now().minusSeconds(60)
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(paymentCode))
                .thenReturn(Optional.of(transaction));
        when(metadataStore.findByPaymentCode(paymentCode)).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(102L)).thenReturn(Optional.of(
                new OrderPaymentSnapshot(102L, 88L, new BigDecimal("250000.00"), com.lumintra.backend.commerce.domain.model.OrderStatus.PENDING_PAYMENT, Instant.now().plusSeconds(900))
        ));
        when(paymentTransactionRepository.markAsSucceededIfPending(eq(5001L), eq("SUCCESS"), any(Instant.class)))
                .thenReturn(1);
        when(paymentGatewayPort.buildQrDetails(any(), any())).thenReturn(null);

        PaymentSessionResponse response = useCase.execute(paymentCode);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);

        ArgumentCaptor<PaymentSucceededEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().customerId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("MOCK-03: Already succeeded transaction returns SUCCEEDED and skips duplicate event")
    void execute_alreadySucceeded_idempotent() {
        String paymentCode = "LMTORD102A1B2C3D4";
        PaymentTransactionEntity transaction = PaymentTransactionEntity.of(
                5001L,
                102L,
                "SEPAY",
                paymentCode,
                new BigDecimal("250000.00"),
                PaymentStatus.SUCCEEDED,
                Instant.now().minusSeconds(30),
                Instant.now().minusSeconds(60)
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(paymentCode))
                .thenReturn(Optional.of(transaction));
        when(metadataStore.findByPaymentCode(paymentCode)).thenReturn(Optional.empty());
        when(paymentGatewayPort.buildQrDetails(any(), any())).thenReturn(null);

        PaymentSessionResponse response = useCase.execute(paymentCode);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentTransactionRepository, never()).markAsSucceededIfPending(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("MOCK-04: Non-existent payment code throws PaymentSessionNotFoundException")
    void execute_nonExistentCode_throwsException() {
        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("UNKNOWN"))
                .isInstanceOf(PaymentSessionNotFoundException.class)
                .hasMessageContaining("Payment session not found.");
    }

    @Test
    @DisplayName("MOCK-05: Blank payment code throws PaymentSessionNotFoundException")
    void execute_blankCode_throwsException() {
        assertThatThrownBy(() -> useCase.execute("  "))
                .isInstanceOf(PaymentSessionNotFoundException.class);
    }
}
