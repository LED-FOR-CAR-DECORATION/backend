package com.lumintra.backend.payment.application;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(SePayProperties.class)
public class GetPaymentSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetPaymentSessionUseCase.class);
    private static final Duration DEFAULT_EXPIRATION_WINDOW = Duration.ofMinutes(15);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderPaymentLookupService orderPaymentLookupService;
    private final PaymentSessionMetadataStore metadataStore;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentSessionMapper paymentSessionMapper;
    private final SePayProperties sePayProperties;

    @Transactional(readOnly = true)
    public PaymentSessionResponse execute(Long customerId, String paymentCode) {
        if (customerId == null || customerId <= 0) {
            throw new AccessDeniedException("Authenticated customer context is required.");
        }
        if (paymentCode == null || paymentCode.isBlank()) {
            throw new PaymentSessionNotFoundException("Payment session not found.");
        }

        String normalizedCode = paymentCode.trim();
        PaymentTransactionEntity transaction = findTransaction(normalizedCode)
                .orElseThrow(() -> {
                    log.warn("Payment session not found [paymentCode={}] [customerId={}]", normalizedCode, customerId);
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

        Optional<OrderPaymentSnapshot> orderSnapshotOpt = targetType == PaymentTargetType.ORDER
                ? orderPaymentLookupService.findByOrderId(transaction.getOrderId())
                : Optional.empty();

        Long ownerCustomerId = metadataOpt
                .map(PaymentSessionMetadata::customerId)
                .or(() -> orderSnapshotOpt.map(OrderPaymentSnapshot::customerId))
                .orElse(null);

        if (ownerCustomerId == null) {
            log.warn("Payment session has no resolvable owner [paymentCode={}] [paymentId={}]",
                    normalizedCode, transaction.getPaymentId());
            throw new PaymentSessionNotFoundException("Payment session not found.");
        }

        if (!ownerCustomerId.equals(customerId)) {
            log.warn("Customer ownership check failed for payment session [paymentCode={}] [customerId={}] [ownerId={}]",
                    normalizedCode, customerId, ownerCustomerId);
            throw new AccessDeniedException("You do not have permission to access this payment session.");
        }

        Instant expiresAt = metadataOpt
                .map(PaymentSessionMetadata::expiresAt)
                .or(() -> orderSnapshotOpt.map(OrderPaymentSnapshot::expiresAt))
                .orElseGet(() -> transaction.getCreatedAt() != null
                        ? transaction.getCreatedAt().plus(DEFAULT_EXPIRATION_WINDOW)
                        : null);

        Instant now = Instant.now();
        PaymentStatus effectiveStatus = resolveEffectiveStatus(transaction, orderSnapshotOpt.orElse(null), expiresAt, now);

        String qrPaymentCode = resolveQrPaymentCode(normalizedCode, transaction);
        SePayQrDetails qrDetails = paymentGatewayPort.buildQrDetails(qrPaymentCode, transaction.getAmount());

        return paymentSessionMapper.toResponse(
                transaction,
                qrPaymentCode,
                targetType,
                qrDetails,
                effectiveStatus,
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

    private PaymentStatus resolveEffectiveStatus(
            PaymentTransactionEntity transaction,
            OrderPaymentSnapshot orderSnapshot,
            Instant expiresAt,
            Instant now) {
        PaymentStatus dbStatus = transaction.getPaymentStatus();
        if (dbStatus != PaymentStatus.PENDING) {
            return dbStatus;
        }
        if (orderSnapshot != null && orderSnapshot.status() == OrderStatus.CANCELED) {
            return PaymentStatus.CANCELLED;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return PaymentStatus.EXPIRED;
        }
        return PaymentStatus.PENDING;
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
