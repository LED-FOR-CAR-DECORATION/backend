package com.lumintra.backend.payment.application;

import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.CreatePaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.model.SePayQrDetails;
import com.lumintra.backend.payment.domain.service.PaymentGatewayPort;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.payment.domain.service.PaymentSessionPort;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.payment.infrastructure.persistence.mapper.PaymentSessionMapper;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CreatePaymentSessionUseCase implements PaymentSessionPort {

    private static final Logger log = LoggerFactory.getLogger(CreatePaymentSessionUseCase.class);
    private static final Duration DEFAULT_ORDER_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_COMMISSION_TTL = Duration.ofHours(24);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentSessionMetadataStore metadataStore;
    private final PaymentSessionMapper paymentSessionMapper;

    @Override
    @Transactional
    public PaymentSessionResponse createSession(CreatePaymentSessionCommand command) {
        validateCommand(command);

        PaymentTargetType targetType = command.targetType();
        BigDecimal normalizedAmount = command.amount().setScale(2, RoundingMode.HALF_UP);
        Duration effectiveTtl = resolveTtl(targetType, command.ttl());
        Instant now = Instant.now();
        Instant expiresAt = now.plus(effectiveTtl);

        String paymentCode = paymentGatewayPort.generatePaymentCode(targetType, command.targetId());
        SePayQrDetails qrDetails = paymentGatewayPort.buildQrDetails(paymentCode, normalizedAmount);

        PaymentTransactionEntity entity = PaymentTransactionEntity.createPending(
                command.targetId(),
                PaymentTransactionEntity.GATEWAY_SEPAY,
                paymentCode,
                normalizedAmount
        );
        PaymentTransactionEntity saved = paymentTransactionRepository.save(entity);
        if (saved.getCreatedAt() == null) {
            saved.setCreatedAt(now);
        }

        PaymentSessionMetadata metadata = new PaymentSessionMetadata(
                paymentCode,
                targetType,
                command.targetId(),
                command.customerId(),
                expiresAt
        );
        metadataStore.save(metadata, effectiveTtl.plusHours(1));

        log.info("Created SePay payment session [paymentId={}] [paymentCode={}] [targetType={}] [targetId={}] [customerId={}]",
                saved.getPaymentId(),
                paymentCode,
                targetType,
                command.targetId(),
                command.customerId());

        return paymentSessionMapper.toResponse(
                saved,
                paymentCode,
                targetType,
                qrDetails,
                PaymentStatus.PENDING,
                expiresAt
        );
    }

    private void validateCommand(CreatePaymentSessionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Payment session command must not be null.");
        }
        if (command.targetType() == null) {
            throw new IllegalArgumentException("Payment targetType is required.");
        }
        if (command.targetId() == null || command.targetId() <= 0) {
            throw new IllegalArgumentException("Payment targetId must be a positive identifier.");
        }
        if (command.customerId() == null || command.customerId() <= 0) {
            throw new IllegalArgumentException("Payment customerId must be a positive identifier.");
        }
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
    }

    private Duration resolveTtl(PaymentTargetType targetType, Duration requestedTtl) {
        if (requestedTtl != null && !requestedTtl.isNegative() && !requestedTtl.isZero()) {
            return requestedTtl;
        }
        return targetType == PaymentTargetType.ORDER ? DEFAULT_ORDER_TTL : DEFAULT_COMMISSION_TTL;
    }
}
