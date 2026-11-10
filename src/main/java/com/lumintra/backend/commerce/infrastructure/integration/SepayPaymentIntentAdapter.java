package com.lumintra.backend.commerce.infrastructure.integration;

import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentIntentResult;
import com.lumintra.backend.commerce.domain.service.PaymentIntentPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment gateway integration adapter that initializes a PENDING payment intent record for SEPAY.
 */
@Component
@RequiredArgsConstructor
public class SepayPaymentIntentAdapter implements PaymentIntentPort {

    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    public PaymentIntentResult initializePaymentIntent(Long orderId, BigDecimal amount, PaymentGateway gateway) {
        String referenceSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String transactionCode = gateway.name() + "-ORD-" + orderId + "-" + referenceSuffix;

        PaymentTransactionEntity saved = paymentTransactionRepository.save(
                PaymentTransactionEntity.createPending(orderId, gateway, transactionCode, amount)
        );

        return new PaymentIntentResult(
                saved.getPaymentId(),
                saved.getGateway(),
                saved.getStatus(),
                saved.getTransactionCode()
        );
    }
}
