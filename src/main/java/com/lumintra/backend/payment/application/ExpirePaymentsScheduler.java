package com.lumintra.backend.payment.application;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.payment.domain.event.PaymentExpiredEvent;
import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExpirePaymentsScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirePaymentsScheduler.class);
    private static final Duration DEFAULT_ORDER_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_COMMISSION_TTL = Duration.ofHours(24);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentSessionMetadataStore metadataStore;
    private final OrderPaymentLookupService orderPaymentLookupService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${app.payment.expiration-check-interval-ms:60000}")
    @Transactional
    public void expirePendingPayments() {
        expirePendingPaymentsAt(Instant.now());
    }

    @Transactional
    public int expirePendingPaymentsAt(Instant now) {
        Instant evaluationTime = now != null ? now : Instant.now();
        List<PaymentTransactionEntity> pendingTransactions = paymentTransactionRepository.findAllPendingTransactions();
        if (pendingTransactions.isEmpty()) {
            return 0;
        }

        int expiredCount = 0;
        for (PaymentTransactionEntity transaction : pendingTransactions) {
            String paymentCode = transaction.getTransactionCode();
            Optional<PaymentSessionMetadata> metadataOpt = (paymentCode != null && !paymentCode.isBlank())
                    ? metadataStore.findByPaymentCode(paymentCode)
                    : Optional.empty();

            PaymentTargetType targetType = metadataOpt
                    .map(PaymentSessionMetadata::targetType)
                    .orElseGet(() -> PaymentTargetType.fromPaymentCode(paymentCode));

            Instant expiresAt = resolveExpiresAt(transaction, metadataOpt, targetType);
            if (expiresAt == null || !expiresAt.isBefore(evaluationTime)) {
                continue;
            }

            int updated = paymentTransactionRepository.markAsExpiredIfPending(
                    transaction.getPaymentId(),
                    PaymentTransactionEntity.DB_STATUS_EXPIRED
            );
            if (updated == 0) {
                log.info("Skipped expiring payment transaction due to concurrent status update [paymentId={}] [paymentCode={}]",
                        transaction.getPaymentId(), paymentCode);
                continue;
            }

            expiredCount++;
            eventPublisher.publishEvent(new PaymentExpiredEvent(
                    transaction.getPaymentId(),
                    paymentCode,
                    targetType,
                    transaction.getOrderId(),
                    evaluationTime
            ));

            log.info("Payment transaction expired [paymentId={}] [paymentCode={}] [targetType={}] [targetId={}] [expiresAt={}]",
                    transaction.getPaymentId(),
                    paymentCode,
                    targetType,
                    transaction.getOrderId(),
                    expiresAt);
        }

        return expiredCount;
    }

    private Instant resolveExpiresAt(
            PaymentTransactionEntity transaction,
            Optional<PaymentSessionMetadata> metadataOpt,
            PaymentTargetType targetType) {
        if (metadataOpt.isPresent() && metadataOpt.get().expiresAt() != null) {
            return metadataOpt.get().expiresAt();
        }
        if (targetType == PaymentTargetType.ORDER && transaction.getOrderId() != null) {
            Optional<Instant> orderExpiresAt = orderPaymentLookupService.findByOrderId(transaction.getOrderId())
                    .map(OrderPaymentSnapshot::expiresAt);
            if (orderExpiresAt.isPresent()) {
                return orderExpiresAt.get();
            }
        }
        if (transaction.getCreatedAt() != null) {
            Duration fallbackTtl = targetType == PaymentTargetType.ORDER ? DEFAULT_ORDER_TTL : DEFAULT_COMMISSION_TTL;
            return transaction.getCreatedAt().plus(fallbackTtl);
        }
        return null;
    }
}
