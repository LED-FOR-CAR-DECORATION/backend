package com.lumintra.backend.payment.application;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(SePayProperties.class)
public class MockPaymentSuccessUseCase {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentSuccessUseCase.class);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentSessionMetadataStore metadataStore;
    private final OrderPaymentLookupService orderPaymentLookupService;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentSessionMapper paymentSessionMapper;
    private final SePayProperties sePayProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PaymentSessionResponse execute(String paymentCode) {
        if (paymentCode == null || paymentCode.isBlank()) {
            throw new PaymentSessionNotFoundException("Payment session not found.");
        }

        String normalizedCode = paymentCode.trim();
        PaymentTransactionEntity transaction = findTransaction(normalizedCode)
                .orElseThrow(() -> {
                    log.warn("Mock payment session not found [paymentCode={}]", normalizedCode);
                    return new PaymentSessionNotFoundException("Payment session not found.");
                });

        Optional<PaymentSessionMetadata> metadataOpt = metadataStore.findByPaymentCode(normalizedCode);
        if (metadataOpt.isEmpty() && transaction.getTransactionCode() != null) {
            metadataOpt = metadataStore.findByPaymentCode(transaction.getTransactionCode());
        }

        PaymentTargetType targetType = metadataOpt
                .map(PaymentSessionMetadata::targetType)
                .orElseGet(() -> PaymentTargetType.fromPaymentCode(
                        transaction.getTransactionCode() != null ? transaction.getTransactionCode() : normalizedCode
                ));

        Long customerId = resolveCustomerId(metadataOpt, targetType, transaction.getOrderId());
        Instant now = Instant.now();

        if (transaction.getPaymentStatus() == PaymentStatus.PENDING) {
            paymentTransactionRepository.markAsSucceededIfPending(
                    transaction.getPaymentId(),
                    PaymentTransactionEntity.DB_STATUS_SUCCESS,
                    now
            );
            transaction.setPaymentStatus(PaymentStatus.SUCCEEDED);
            transaction.setPaidAt(now);

            eventPublisher.publishEvent(new PaymentSucceededEvent(
                    transaction.getPaymentId(),
                    normalizedCode,
                    targetType,
                    transaction.getOrderId(),
                    customerId,
                    transaction.getAmount(),
                    System.currentTimeMillis(),
                    "MOCK-" + normalizedCode,
                    now
            ));

            log.info("Mock payment succeeded event published [paymentId={}] [paymentCode={}] [targetType={}] [targetId={}]",
                    transaction.getPaymentId(), normalizedCode, targetType, transaction.getOrderId());
        } else {
            log.info("Mock payment called for transaction already in status {} [paymentId={}] [paymentCode={}]",
                    transaction.getPaymentStatus(), transaction.getPaymentId(), normalizedCode);
        }

        String qrPaymentCode = resolveQrPaymentCode(normalizedCode, transaction);
        SePayQrDetails qrDetails = paymentGatewayPort.buildQrDetails(qrPaymentCode, transaction.getAmount());
        Instant expiresAt = metadataOpt.map(PaymentSessionMetadata::expiresAt).orElse(null);

        return paymentSessionMapper.toResponse(
                transaction,
                qrPaymentCode,
                targetType,
                qrDetails,
                PaymentStatus.SUCCEEDED,
                expiresAt
        );
    }

    private Optional<PaymentTransactionEntity> findTransaction(String paymentCode) {
        Optional<PaymentTransactionEntity> byCode = paymentTransactionRepository
                .findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(paymentCode);
        if (byCode.isPresent()) {
            return byCode;
        }

        String upper = paymentCode.toUpperCase(Locale.ROOT);
        if (!upper.equals(paymentCode)) {
            Optional<PaymentTransactionEntity> byUpper = paymentTransactionRepository
                    .findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(upper);
            if (byUpper.isPresent()) {
                return byUpper;
            }
        }

        return extractOrderIdFromPrefixedCode(upper)
                .flatMap(paymentTransactionRepository::findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc);
    }

    private Optional<Long> extractOrderIdFromPrefixedCode(String upperPaymentCode) {
        String prefix = sePayProperties.normalizedPaymentPrefix();
        if (!upperPaymentCode.startsWith(prefix) || upperPaymentCode.length() <= prefix.length()) {
            return Optional.empty();
        }
        String remainder = upperPaymentCode.substring(prefix.length());
        for (int i = 0; i < remainder.length(); i++) {
            if (!Character.isDigit(remainder.charAt(i))) {
                return Optional.empty();
            }
        }
        try {
            long parsedOrderId = Long.parseLong(remainder);
            return parsedOrderId > 0 ? Optional.of(parsedOrderId) : Optional.empty();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private Long resolveCustomerId(
            Optional<PaymentSessionMetadata> metadataOpt,
            PaymentTargetType targetType,
            Long targetId) {
        if (metadataOpt.isPresent() && metadataOpt.get().customerId() != null) {
            return metadataOpt.get().customerId();
        }
        if (targetType == PaymentTargetType.ORDER && targetId != null) {
            return orderPaymentLookupService.findByOrderId(targetId)
                    .map(OrderPaymentSnapshot::customerId)
                    .orElse(null);
        }
        return null;
    }

    private String resolveQrPaymentCode(String requestedPaymentCode, PaymentTransactionEntity transaction) {
        String prefix = sePayProperties.normalizedPaymentPrefix();
        if (requestedPaymentCode.toUpperCase(Locale.ROOT).startsWith(prefix)) {
            return requestedPaymentCode.toUpperCase(Locale.ROOT);
        }
        if (transaction.getTransactionCode() != null && !transaction.getTransactionCode().isBlank()) {
            return transaction.getTransactionCode();
        }
        return requestedPaymentCode;
    }
}
