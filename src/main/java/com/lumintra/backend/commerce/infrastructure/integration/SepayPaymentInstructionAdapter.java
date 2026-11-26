package com.lumintra.backend.commerce.infrastructure.integration;

import com.lumintra.backend.commerce.domain.model.SepayPaymentInstructions;
import com.lumintra.backend.commerce.domain.service.PaymentInstructionPort;
import com.lumintra.backend.payment.domain.model.SePayQrDetails;
import com.lumintra.backend.payment.domain.service.PaymentGatewayPort;
import com.lumintra.backend.payment.infrastructure.integration.SePayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SePay VietQR integration adapter that delegates QR generation for pending orders
 * to the centralized {@link PaymentGatewayPort} in the payment module.
 */
@Component
@RequiredArgsConstructor
public class SepayPaymentInstructionAdapter implements PaymentInstructionPort {

    private final PaymentGatewayPort paymentGatewayPort;
    private final SePayProperties sePayProperties;

    @Override
    public SepayPaymentInstructions buildInstructions(
            Long orderId,
            String transactionCode,
            BigDecimal amount,
            Instant expiresAt) {
        String paymentContent = (transactionCode != null && !transactionCode.isBlank())
                ? transactionCode.trim()
                : sePayProperties.normalizedPaymentPrefix() + orderId;
        SePayQrDetails qrDetails = paymentGatewayPort.buildQrDetails(paymentContent, amount);

        return new SepayPaymentInstructions(
                qrDetails.paymentCode(),
                qrDetails.qrUrl(),
                qrDetails.accountNumber(),
                qrDetails.bankName(),
                expiresAt
        );
    }
}
