package com.lumintra.backend.commerce.infrastructure.integration;

import com.lumintra.backend.commerce.domain.model.SepayPaymentInstructions;
import com.lumintra.backend.commerce.domain.service.PaymentInstructionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SePay VietQR integration adapter generating active bank-transfer retry details for pending orders.
 */
@Component
public class SepayPaymentInstructionAdapter implements PaymentInstructionPort {

    private static final String PAYMENT_CONTENT_PREFIX = "LUM";

    private final String qrBaseUrl;
    private final String bankAccount;
    private final String bankName;

    public SepayPaymentInstructionAdapter(
            @Value("${app.sepay.qr-base-url:https://qr.sepay.vn/img}") String qrBaseUrl,
            @Value("${app.sepay.bank-account:0123456789}") String bankAccount,
            @Value("${app.sepay.bank-name:MBBank}") String bankName) {
        this.qrBaseUrl = qrBaseUrl;
        this.bankAccount = bankAccount;
        this.bankName = bankName;
    }

    @Override
    public SepayPaymentInstructions buildInstructions(Long orderId, BigDecimal amount, Instant expiresAt) {
        String paymentContent = PAYMENT_CONTENT_PREFIX + orderId;
        String formattedAmount = (amount != null ? amount : BigDecimal.ZERO)
                .stripTrailingZeros()
                .toPlainString();

        String qrCodeUrl = UriComponentsBuilder.fromUriString(qrBaseUrl)
                .queryParam("acc", bankAccount)
                .queryParam("bank", bankName)
                .queryParam("amount", formattedAmount)
                .queryParam("des", paymentContent)
                .build()
                .encode()
                .toUriString();

        return new SepayPaymentInstructions(
                paymentContent,
                qrCodeUrl,
                bankAccount,
                bankName,
                expiresAt
        );
    }
}
