package com.lumintra.backend.payment;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.application.GetPaymentSessionUseCase;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPaymentSessionUseCaseTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private OrderPaymentLookupService orderPaymentLookupService;

    @Mock
    private PaymentSessionMetadataStore metadataStore;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    private PaymentSessionMapper paymentSessionMapper;
    private SePayProperties sePayProperties;
    private GetPaymentSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        paymentSessionMapper = new PaymentSessionMapper();
        sePayProperties = new SePayProperties(
                "https://vietqr.app/img",
                "TPBank",
                "10006140844",
                "NGUYEN QUANG KHANH",
                "LUM",
                "secret-webhook-key"
        );
        useCase = new GetPaymentSessionUseCase(
                paymentTransactionRepository,
                orderPaymentLookupService,
                metadataStore,
                paymentGatewayPort,
                paymentSessionMapper,
                sePayProperties
        );
    }

    @Test
    @DisplayName("PAY-01 [N]: Retrieves active PENDING commission session using cached PaymentSessionMetadata and returns QR URL")
    void execute_activeCommissionSessionWithMetadata_returnsPendingWithQrUrl() {
        String code = "LUMCMS205ABCD1234";
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(3600);

        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                701L,
                205L,
                "SEPAY",
                code,
                new BigDecimal("500000.00"),
                PaymentStatus.PENDING,
                null,
                now.minusSeconds(60)
        );
        PaymentSessionMetadata metadata = new PaymentSessionMetadata(
                code,
                PaymentTargetType.COMMISSION_INITIAL,
                205L,
                42L,
                expiresAt
        );
        SePayQrDetails qrDetails = new SePayQrDetails(
                code,
                "https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=500000&des=" + code + "&showinfo=true&holder=NGUYEN%20QUANG%20KHANH",
                "TPBank",
                "10006140844",
                "NGUYEN QUANG KHANH"
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(code))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode(code)).thenReturn(Optional.of(metadata));
        when(paymentGatewayPort.buildQrDetails(code, new BigDecimal("500000.00"))).thenReturn(qrDetails);

        PaymentSessionResponse response = useCase.execute(42L, code);

        assertThat(response.paymentId()).isEqualTo(701L);
        assertThat(response.paymentCode()).isEqualTo(code);
        assertThat(response.targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(response.targetId()).isEqualTo(205L);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.qrUrl()).isEqualTo(qrDetails.qrUrl());
        assertThat(response.bankName()).isEqualTo("TPBank");
        assertThat(response.accountNumber()).isEqualTo("10006140844");
        assertThat(response.accountName()).isEqualTo("NGUYEN QUANG KHANH");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("PAY-01 [N]: Retrieves order payment session via prefixed LUM{orderId} fallback and OrderPaymentLookupService")
    void execute_prefixedOrderCodeFallback_resolvesOrderOwnershipAndExpiration() {
        String code = "LUM102";
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(600);

        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                501L,
                102L,
                "SEPAY",
                "SEPAY-ORD-102-AABBCCDD",
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                now.minusSeconds(120)
        );
        OrderPaymentSnapshot orderSnapshot = new OrderPaymentSnapshot(
                102L,
                42L,
                new BigDecimal("250000.00"),
                OrderStatus.PENDING_PAYMENT,
                expiresAt
        );
        SePayQrDetails qrDetails = new SePayQrDetails(
                code,
                "https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=LUM102&showinfo=true&holder=NGUYEN%20QUANG%20KHANH",
                "TPBank",
                "10006140844",
                "NGUYEN QUANG KHANH"
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(any()))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(102L))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode(any())).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(102L)).thenReturn(Optional.of(orderSnapshot));
        when(paymentGatewayPort.buildQrDetails(eq("LUM102"), eq(new BigDecimal("250000.00")))).thenReturn(qrDetails);

        PaymentSessionResponse response = useCase.execute(42L, "lum102");

        assertThat(response.paymentId()).isEqualTo(501L);
        assertThat(response.paymentCode()).isEqualTo("LUM102");
        assertThat(response.targetType()).isEqualTo(PaymentTargetType.ORDER);
        assertThat(response.targetId()).isEqualTo(102L);
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.qrUrl()).isEqualTo(qrDetails.qrUrl());
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("PAY-01 [B]: Past expiresAt marks PENDING transaction as EXPIRED and omits qrUrl")
    void execute_pendingPastExpiration_returnsExpiredStatusAndNullQrUrl() {
        String code = "LUMORD105EXPIRED1";
        Instant now = Instant.now();
        Instant pastExpiresAt = now.minusSeconds(60);

        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                505L,
                105L,
                "SEPAY",
                code,
                new BigDecimal("300000.00"),
                PaymentStatus.PENDING,
                null,
                now.minusSeconds(960)
        );
        OrderPaymentSnapshot orderSnapshot = new OrderPaymentSnapshot(
                105L,
                42L,
                new BigDecimal("300000.00"),
                OrderStatus.PENDING_PAYMENT,
                pastExpiresAt
        );
        SePayQrDetails qrDetails = new SePayQrDetails(
                code,
                "https://qr.sepay.vn/img?acc=0123456789&bank=MBBank&amount=300000&des=" + code,
                "MBBank",
                "0123456789",
                "LUMINTRA"
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(code))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode(code)).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(105L)).thenReturn(Optional.of(orderSnapshot));
        when(paymentGatewayPort.buildQrDetails(code, new BigDecimal("300000.00"))).thenReturn(qrDetails);

        PaymentSessionResponse response = useCase.execute(42L, code);

        assertThat(response.status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(response.qrUrl()).isNull();
    }

    @Test
    @DisplayName("PAY-01 [N]: Settled SUCCESS database transaction returns SUCCEEDED with paidAt and null qrUrl")
    void execute_settledTransaction_returnsSucceededAndNullQrUrl() {
        String code = "LUMORD108PAID1234";
        Instant createdAt = Instant.parse("2026-11-12T16:00:00Z");
        Instant paidAt = Instant.parse("2026-11-12T16:03:00Z");
        Instant expiresAt = Instant.parse("2026-11-12T16:15:00Z");

        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                508L,
                108L,
                "SEPAY",
                code,
                new BigDecimal("450000.00"),
                PaymentStatus.SUCCEEDED,
                paidAt,
                createdAt
        );
        tx.setStatus("SUCCESS");

        OrderPaymentSnapshot orderSnapshot = new OrderPaymentSnapshot(
                108L,
                42L,
                new BigDecimal("450000.00"),
                OrderStatus.PROCESSING,
                expiresAt
        );
        SePayQrDetails qrDetails = new SePayQrDetails(
                code,
                "https://qr.sepay.vn/img?acc=0123456789&bank=MBBank&amount=450000&des=" + code,
                "MBBank",
                "0123456789",
                "LUMINTRA"
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(code))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode(code)).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(108L)).thenReturn(Optional.of(orderSnapshot));
        when(paymentGatewayPort.buildQrDetails(code, new BigDecimal("450000.00"))).thenReturn(qrDetails);

        PaymentSessionResponse response = useCase.execute(42L, code);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.paidAt()).isEqualTo(paidAt);
        assertThat(response.qrUrl()).isNull();
    }

    @Test
    @DisplayName("PAY-01 [A]: Throws AccessDeniedException when customerId does not match session owner")
    void execute_differentCustomerOwner_throwsAccessDeniedException() {
        String code = "LUMORD102A1B2C3D4";
        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                501L,
                102L,
                "SEPAY",
                code,
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                Instant.now()
        );
        OrderPaymentSnapshot orderSnapshot = new OrderPaymentSnapshot(
                102L,
                42L,
                new BigDecimal("250000.00"),
                OrderStatus.PENDING_PAYMENT,
                Instant.now().plusSeconds(600)
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(code))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode(code)).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(102L)).thenReturn(Optional.of(orderSnapshot));

        assertThatThrownBy(() -> useCase.execute(99L, code))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("permission");
    }

    @Test
    @DisplayName("PAY-01 [A]: Throws PaymentSessionNotFoundException when paymentCode does not match any transaction")
    void execute_unknownPaymentCode_throwsPaymentSessionNotFoundException() {
        String code = "LUMUNKNOWN999";
        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(42L, code))
                .isInstanceOf(PaymentSessionNotFoundException.class);
    }

    @Test
    @DisplayName("PAY-01 [A]: Throws AccessDeniedException when customerId is null or non-positive")
    void execute_nullOrNonPositiveCustomerId_throwsAccessDeniedException() {
        assertThatThrownBy(() -> useCase.execute(null, "LUMORD102A1B2C3D4"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authenticated customer context is required.");

        assertThatThrownBy(() -> useCase.execute(0L, "LUMORD102A1B2C3D4"))
                .isInstanceOf(AccessDeniedException.class);

        assertThatThrownBy(() -> useCase.execute(-5L, "LUMORD102A1B2C3D4"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("PAY-01 [A]: Throws PaymentSessionNotFoundException when paymentCode is null or blank")
    void execute_nullOrBlankPaymentCode_throwsPaymentSessionNotFoundException() {
        assertThatThrownBy(() -> useCase.execute(42L, null))
                .isInstanceOf(PaymentSessionNotFoundException.class);

        assertThatThrownBy(() -> useCase.execute(42L, "   "))
                .isInstanceOf(PaymentSessionNotFoundException.class);
    }

    @Test
    @DisplayName("PAY-01 [A]: Throws PaymentSessionNotFoundException when payment session has no resolvable owner")
    void execute_unresolvableOwner_throwsPaymentSessionNotFoundException() {
        String code = "LUMORD102A1B2C3D4";
        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                501L,
                102L,
                "SEPAY",
                code,
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                Instant.now()
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(code))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode(code)).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(102L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(42L, code))
                .isInstanceOf(PaymentSessionNotFoundException.class)
                .hasMessageContaining("Payment session not found.");
    }

    @Test
    @DisplayName("PAY-01 [N]: Canceled order marks PENDING transaction as CANCELLED")
    void execute_canceledOrder_resolvesToCancelledStatus() {
        String code = "LUMORD102CANCELLED1";
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(600);

        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                502L,
                102L,
                "SEPAY",
                code,
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                now.minusSeconds(60)
        );
        OrderPaymentSnapshot orderSnapshot = new OrderPaymentSnapshot(
                102L,
                42L,
                new BigDecimal("250000.00"),
                OrderStatus.CANCELED,
                expiresAt
        );
        SePayQrDetails qrDetails = new SePayQrDetails(
                code,
                "https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=" + code + "&showinfo=true&holder=NGUYEN%20QUANG%20KHANH",
                "TPBank",
                "10006140844",
                "NGUYEN QUANG KHANH"
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(code))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode(code)).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(102L)).thenReturn(Optional.of(orderSnapshot));
        when(paymentGatewayPort.buildQrDetails(code, new BigDecimal("250000.00"))).thenReturn(qrDetails);

        PaymentSessionResponse response = useCase.execute(42L, code);

        assertThat(response.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(response.qrUrl()).isNull();
    }

    @Test
    @DisplayName("PAY-01 [N]: Falls back to createdAt plus 15 minutes when no explicit expiresAt is present")
    void execute_noExplicitExpiresAt_fallsBackToCreatedAtPlus15m() {
        String code = "LUMORD102NOEXPIRY";
        Instant createdAt = Instant.parse("2026-11-12T16:00:00Z");

        PaymentTransactionEntity tx = PaymentTransactionEntity.of(
                503L,
                102L,
                "SEPAY",
                code,
                new BigDecimal("250000.00"),
                PaymentStatus.PENDING,
                null,
                createdAt
        );
        OrderPaymentSnapshot orderSnapshot = new OrderPaymentSnapshot(
                102L,
                42L,
                new BigDecimal("250000.00"),
                OrderStatus.PENDING_PAYMENT,
                null
        );
        SePayQrDetails qrDetails = new SePayQrDetails(
                code,
                "https://vietqr.app/img?bank=TPBank&acc=10006140844&template=compact&amount=250000&des=" + code + "&showinfo=true&holder=NGUYEN%20QUANG%20KHANH",
                "TPBank",
                "10006140844",
                "NGUYEN QUANG KHANH"
        );

        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(code))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode(code)).thenReturn(Optional.empty());
        when(orderPaymentLookupService.findByOrderId(102L)).thenReturn(Optional.of(orderSnapshot));
        when(paymentGatewayPort.buildQrDetails(code, new BigDecimal("250000.00"))).thenReturn(qrDetails);

        PaymentSessionResponse response = useCase.execute(42L, code);

        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-11-12T16:15:00Z"));
    }
}
