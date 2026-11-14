package com.lumintra.backend.payment;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.payment.api.request.SePayWebhookRequest;
import com.lumintra.backend.payment.api.response.SePayWebhookResponse;
import com.lumintra.backend.payment.application.ProcessSePayWebhookUseCase;
import com.lumintra.backend.payment.domain.event.PaymentSucceededEvent;
import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.payment.domain.service.PaymentWebhookIdempotencyStore;
import com.lumintra.backend.payment.infrastructure.integration.SePayProperties;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessSePayWebhookUseCaseTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentSessionMetadataStore metadataStore;
    @Mock
    private PaymentWebhookIdempotencyStore idempotencyStore;
    @Mock
    private OrderPaymentLookupService orderPaymentLookupService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SePayProperties sePayProperties;
    private ProcessSePayWebhookUseCase useCase;

    @BeforeEach
    void setUp() {
        sePayProperties = new SePayProperties(
                "https://vietqr.app/img",
                "TPBank",
                "10006140844",
                "NGUYEN QUANG KHANH",
                "LMT",
                "sepay-webhook-secret"
        );
        useCase = new ProcessSePayWebhookUseCase(
                paymentTransactionRepository,
                metadataStore,
                idempotencyStore,
                orderPaymentLookupService,
                sePayProperties,
                eventPublisher
        );
    }

    @Test
    @DisplayName("PAY-02 [N]: Authorization: Apikey valid matches exact code, updates status to SUCCESS, marks idempotency, and publishes PaymentSucceededEvent")
    void execute_authorizationApikeyValid_processesPaymentAndPublishesEvent() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Apikey sepay-webhook-secret");

        SePayWebhookRequest request = new SePayWebhookRequest(
                92704L,
                "TPBank",
                "2026-11-13 01:02:03",
                "10006140844",
                "LMTORD102A1B2C3D4",
                "Chuyen khoan LMTORD102A1B2C3D4",
                "in",
                new BigDecimal("250000"),
                new BigDecimal("1000000"),
                null,
                "FT263170001",
                "Bank transfer"
        );

        PaymentTransactionEntity tx = pendingTransaction(10L, 102L, "LMTORD102A1B2C3D4", "250000.00");
        when(idempotencyStore.isProcessed(92704L)).thenReturn(false);
        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc("LMTORD102A1B2C3D4"))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode("LMTORD102A1B2C3D4")).thenReturn(Optional.of(new PaymentSessionMetadata(
                "LMTORD102A1B2C3D4",
                PaymentTargetType.ORDER,
                102L,
                42L,
                Instant.parse("2026-11-14T00:00:00Z")
        )));
        when(paymentTransactionRepository.markAsSucceededIfPending(
                eq(10L),
                eq(PaymentTransactionEntity.DB_STATUS_SUCCESS),
                any(Instant.class)
        )).thenReturn(1);

        SePayWebhookResponse response = useCase.execute(headers, request);

        assertThat(response.sepayTxId()).isEqualTo(92704L);
        assertThat(response.paymentId()).isEqualTo(10L);
        assertThat(response.paymentCode()).isEqualTo("LMTORD102A1B2C3D4");
        assertThat(response.targetType()).isEqualTo(PaymentTargetType.ORDER);
        assertThat(response.targetId()).isEqualTo(102L);
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.outcome()).isEqualTo("PROCESSED");

        verify(idempotencyStore).markProcessed(eq(92704L), eq("LMTORD102A1B2C3D4"), eq(Duration.ofDays(7)));

        ArgumentCaptor<PaymentSucceededEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PaymentSucceededEvent event = eventCaptor.getValue();
        assertThat(event.paymentId()).isEqualTo(10L);
        assertThat(event.paymentCode()).isEqualTo("LMTORD102A1B2C3D4");
        assertThat(event.targetType()).isEqualTo(PaymentTargetType.ORDER);
        assertThat(event.targetId()).isEqualTo(102L);
        assertThat(event.customerId()).isEqualTo(42L);
        assertThat(event.amount()).isEqualByComparingTo("250000.00");
        assertThat(event.sepayTxId()).isEqualTo(92704L);
        assertThat(event.referenceCode()).isEqualTo("FT263170001");
        assertThat(event.paidAt()).isNotNull();
    }

    @Test
    @DisplayName("PAY-02 [N]: Direct Apikey header extracts code from description regex and accepts overpayment")
    void execute_directApikeyHeaderAndRegexExtractionWithOverpayment_processesPayment() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Apikey", "sepay-webhook-secret");

        SePayWebhookRequest request = new SePayWebhookRequest(
                92705L,
                "TPBank",
                "2026-11-13 02:00:00",
                "10006140844",
                null,
                "   ",
                "in",
                new BigDecimal("300000"),
                null,
                null,
                "REF-OVERPAY",
                "TK 10006140844 +300,000 VND ND: lmtcms501abcd1234"
        );

        PaymentTransactionEntity tx = pendingTransaction(11L, 501L, "LMTCMS501ABCD1234", "250000.00");
        when(idempotencyStore.isProcessed(92705L)).thenReturn(false);
        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc("LMTCMS501ABCD1234"))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode("LMTCMS501ABCD1234")).thenReturn(Optional.of(new PaymentSessionMetadata(
                "LMTCMS501ABCD1234",
                PaymentTargetType.COMMISSION_INITIAL,
                501L,
                77L,
                Instant.parse("2026-11-14T00:00:00Z")
        )));
        when(paymentTransactionRepository.markAsSucceededIfPending(
                eq(11L),
                eq(PaymentTransactionEntity.DB_STATUS_SUCCESS),
                any(Instant.class)
        )).thenReturn(1);

        SePayWebhookResponse response = useCase.execute(headers, request);

        assertThat(response.outcome()).isEqualTo("PROCESSED");
        assertThat(response.targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(response.targetId()).isEqualTo(501L);

        ArgumentCaptor<PaymentSucceededEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().customerId()).isEqualTo(77L);
        assertThat(eventCaptor.getValue().amount()).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("PAY-02 [N]: Falls back to LMT{orderId} lookup and OrderPaymentLookupService when metadata cache is empty")
    void execute_legacyOrderIdFallback_processesAndResolvesCustomerIdFromOrderService() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Apikey sepay-webhook-secret");

        SePayWebhookRequest request = new SePayWebhookRequest(
                92706L,
                "TPBank",
                null,
                "10006140844",
                null,
                "Thanh toan don hang LMT102",
                "in",
                new BigDecimal("250000"),
                null,
                null,
                "REF-102",
                null
        );

        PaymentTransactionEntity tx = pendingTransaction(12L, 102L, "LMTORD102FALLBACK", "250000.00");
        when(idempotencyStore.isProcessed(92706L)).thenReturn(false);
        when(paymentTransactionRepository.findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(102L))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode("LMTORD102FALLBACK")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.markAsSucceededIfPending(
                eq(12L),
                eq(PaymentTransactionEntity.DB_STATUS_SUCCESS),
                any(Instant.class)
        )).thenReturn(1);
        when(orderPaymentLookupService.findByOrderId(102L)).thenReturn(Optional.of(new OrderPaymentSnapshot(
                102L,
                88L,
                new BigDecimal("250000.00"),
                OrderStatus.PENDING_PAYMENT,
                Instant.parse("2026-11-14T00:00:00Z")
        )));

        SePayWebhookResponse response = useCase.execute(headers, request);

        assertThat(response.outcome()).isEqualTo("PROCESSED");
        assertThat(response.paymentCode()).isEqualTo("LMTORD102FALLBACK");
        assertThat(response.targetType()).isEqualTo(PaymentTargetType.ORDER);

        ArgumentCaptor<PaymentSucceededEvent> eventCaptor = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().customerId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("PAY-02 [A]: Throws BadCredentialsException on missing Apikey header")
    void execute_missingApikeyHeader_throwsBadCredentials() {
        SePayWebhookRequest request = validOrderRequest("LMTORD102A1B2C3D4", "250000");

        assertThatThrownBy(() -> useCase.execute(new HttpHeaders(), request))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(idempotencyStore, paymentTransactionRepository, eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [A]: Throws BadCredentialsException on wrong Apikey or non-Apikey Authorization scheme")
    void execute_wrongApikeyOrBearerScheme_throwsBadCredentials() {
        SePayWebhookRequest request = validOrderRequest("LMTORD102A1B2C3D4", "250000");

        HttpHeaders bearerHeaders = new HttpHeaders();
        bearerHeaders.set(HttpHeaders.AUTHORIZATION, "Bearer sepay-webhook-secret");

        assertThatThrownBy(() -> useCase.execute(bearerHeaders, request))
                .isInstanceOf(BadCredentialsException.class);

        HttpHeaders wrongApikeyHeaders = new HttpHeaders();
        wrongApikeyHeaders.set("Apikey", "wrong-secret");

        assertThatThrownBy(() -> useCase.execute(wrongApikeyHeaders, request))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(idempotencyStore, paymentTransactionRepository, eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [A]: Throws BadCredentialsException when webhook secret is not configured")
    void execute_unconfiguredSecret_throwsBadCredentials() {
        ProcessSePayWebhookUseCase misconfiguredUseCase = new ProcessSePayWebhookUseCase(
                paymentTransactionRepository,
                metadataStore,
                idempotencyStore,
                orderPaymentLookupService,
                new SePayProperties("https://vietqr.app/img", "TPBank", "10006140844", "HOLDER", "LMT", "   "),
                eventPublisher
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("Apikey", "any-key");

        assertThatThrownBy(() -> misconfiguredUseCase.execute(headers, validOrderRequest("LMTORD102A1B2C3D4", "250000")))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(idempotencyStore, paymentTransactionRepository, eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [B]: Returns IGNORED without DB/cache writes when sepay_tx_id is 0 (SePay dashboard test ping)")
    void execute_zeroSepayTxIdTestPing_returnsIgnoredWithoutSideEffects() {
        HttpHeaders headers = authorizedHeaders();
        SePayWebhookRequest testPing = new SePayWebhookRequest(
                0L, "TPBank", "2026-11-13 04:01:30", "10006140844", null,
                "Test webhook", "in", new BigDecimal("10000"),
                null, null, null, null
        );

        SePayWebhookResponse response = useCase.execute(headers, testPing);

        assertThat(response.sepayTxId()).isZero();
        assertThat(response.outcome()).isEqualTo("IGNORED");
        verifyNoInteractions(idempotencyStore, paymentTransactionRepository, eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [A]: Returns IGNORED when sepay_tx_id was already processed (idempotency)")
    void execute_duplicateSepayTxId_returnsIgnored() {
        HttpHeaders headers = authorizedHeaders();
        when(idempotencyStore.isProcessed(92704L)).thenReturn(true);

        SePayWebhookResponse response = useCase.execute(headers, validOrderRequest("LMTORD102A1B2C3D4", "250000"));

        assertThat(response.outcome()).isEqualTo("IGNORED");
        assertThat(response.sepayTxId()).isEqualTo(92704L);
        verifyNoInteractions(paymentTransactionRepository, eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [B]: Returns IGNORED for non-incoming transferType ('out')")
    void execute_outgoingTransfer_returnsIgnored() {
        HttpHeaders headers = authorizedHeaders();
        when(idempotencyStore.isProcessed(92704L)).thenReturn(false);

        SePayWebhookRequest outgoing = new SePayWebhookRequest(
                92704L, "TPBank", null, "10006140844", "LMTORD102A1B2C3D4",
                "LMTORD102A1B2C3D4", "out", new BigDecimal("250000"),
                null, null, null, null
        );

        SePayWebhookResponse response = useCase.execute(headers, outgoing);

        assertThat(response.outcome()).isEqualTo("IGNORED");
        verifyNoInteractions(paymentTransactionRepository, eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [A]: Returns UNMATCHED when no payment transaction matches")
    void execute_noMatchingTransaction_returnsUnmatched() {
        HttpHeaders headers = authorizedHeaders();
        when(idempotencyStore.isProcessed(92704L)).thenReturn(false);
        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc("LMTORD999MISSING"))
                .thenReturn(Optional.empty());

        SePayWebhookResponse response = useCase.execute(headers, validOrderRequest("LMTORD999MISSING", "250000"));

        assertThat(response.outcome()).isEqualTo("UNMATCHED");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [B]: Returns IGNORED when matched transaction is already EXPIRED (terminal status)")
    void execute_expiredTransaction_returnsIgnored() {
        HttpHeaders headers = authorizedHeaders();
        PaymentTransactionEntity tx = pendingTransaction(13L, 103L, "LMTORD103EXPIRED1", "250000.00");
        tx.setPaymentStatus(PaymentStatus.EXPIRED);

        when(idempotencyStore.isProcessed(92704L)).thenReturn(false);
        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc("LMTORD103EXPIRED1"))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode("LMTORD103EXPIRED1")).thenReturn(Optional.empty());

        SePayWebhookResponse response = useCase.execute(headers, validOrderRequest("LMTORD103EXPIRED1", "250000"));

        assertThat(response.outcome()).isEqualTo("IGNORED");
        assertThat(response.status()).isEqualTo(PaymentStatus.EXPIRED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [A]: Returns IGNORED when transferAmount is less than expected amount")
    void execute_insufficientAmount_returnsIgnoredWithoutUpdatingStatus() {
        HttpHeaders headers = authorizedHeaders();
        PaymentTransactionEntity tx = pendingTransaction(14L, 104L, "LMTORD104UNDERPAY", "250000.00");

        when(idempotencyStore.isProcessed(92704L)).thenReturn(false);
        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc("LMTORD104UNDERPAY"))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode("LMTORD104UNDERPAY")).thenReturn(Optional.empty());

        SePayWebhookResponse response = useCase.execute(headers, validOrderRequest("LMTORD104UNDERPAY", "249999"));

        assertThat(response.outcome()).isEqualTo("IGNORED");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("PAY-02 [B]: Returns IGNORED and avoids duplicate event when concurrent update wins (updated == 0)")
    void execute_concurrentUpdateLosesRace_returnsIgnoredAndDoesNotPublishEvent() {
        HttpHeaders headers = authorizedHeaders();
        PaymentTransactionEntity tx = pendingTransaction(15L, 105L, "LMTORD105CONCURR", "250000.00");

        when(idempotencyStore.isProcessed(92704L)).thenReturn(false);
        when(paymentTransactionRepository.findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc("LMTORD105CONCURR"))
                .thenReturn(Optional.of(tx));
        when(metadataStore.findByPaymentCode("LMTORD105CONCURR")).thenReturn(Optional.empty());
        when(paymentTransactionRepository.markAsSucceededIfPending(
                eq(15L),
                eq(PaymentTransactionEntity.DB_STATUS_SUCCESS),
                any(Instant.class)
        )).thenReturn(0);

        SePayWebhookResponse response = useCase.execute(headers, validOrderRequest("LMTORD105CONCURR", "250000"));

        assertThat(response.outcome()).isEqualTo("IGNORED");
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(idempotencyStore).markProcessed(eq(92704L), eq("LMTORD105CONCURR"), eq(Duration.ofDays(7)));
        verifyNoInteractions(eventPublisher);
    }

    private HttpHeaders authorizedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Apikey sepay-webhook-secret");
        return headers;
    }

    private SePayWebhookRequest validOrderRequest(String code, String amount) {
        return new SePayWebhookRequest(
                92704L,
                "TPBank",
                "2026-11-13 01:02:03",
                "10006140844",
                code,
                code,
                "in",
                new BigDecimal(amount),
                null,
                null,
                "FT263170001",
                code
        );
    }

    private PaymentTransactionEntity pendingTransaction(Long paymentId, Long orderId, String code, String amount) {
        PaymentTransactionEntity entity = PaymentTransactionEntity.createPending(
                orderId,
                "SEPAY",
                code,
                new BigDecimal(amount)
        );
        ReflectionTestUtils.setField(entity, "paymentId", paymentId);
        return entity;
    }
}
