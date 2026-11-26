package com.lumintra.backend.payment.infrastructure.integration;

import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.model.SePayQrDetails;
import com.lumintra.backend.payment.domain.service.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * SePay VietQR integration adapter responsible for generating unique transfer codes
 * and building dynamic VietQR image URLs.
 */
@Component
@EnableConfigurationProperties(SePayProperties.class)
public class SePayGatewayAdapter implements PaymentGatewayPort {

    private static final String QR_TEMPLATE = "compact";
    private static final String QR_SHOW_INFO = "true";

    private final SePayProperties properties;

    @Autowired
    public SePayGatewayAdapter(SePayProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generatePaymentCode(PaymentTargetType targetType, Long targetId) {
        if (targetId == null || targetId <= 0) {
            throw new IllegalArgumentException("targetId must be a positive identifier.");
        }
        PaymentTargetType resolvedType = targetType != null ? targetType : PaymentTargetType.ORDER;
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        return properties.normalizedPaymentPrefix()
                + resolvedType.getCodeSegment()
                + targetId
                + suffix;
    }

    @Override
    public SePayQrDetails buildQrDetails(String paymentCode, BigDecimal amount) {
        if (paymentCode == null || paymentCode.isBlank()) {
            throw new IllegalArgumentException("paymentCode must not be blank.");
        }
        String normalizedCode = paymentCode.trim();
        BigDecimal resolvedAmount = amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                ? amount
                : BigDecimal.ZERO;
        String formattedAmount = resolvedAmount.stripTrailingZeros().toPlainString();

        String bankName = properties.normalizedBankName();
        String accountNumber = properties.resolvedAccountNumber();
        String accountName = properties.normalizedAccountName();

        String qrUrl = UriComponentsBuilder.fromUriString(properties.normalizedQrBaseUrl())
                .queryParam("bank", bankName)
                .queryParam("acc", accountNumber)
                .queryParam("template", QR_TEMPLATE)
                .queryParam("amount", formattedAmount)
                .queryParam("des", normalizedCode)
                .queryParam("showinfo", QR_SHOW_INFO)
                .queryParam("holder", accountName)
                .build()
                .encode()
                .toUriString();

        return new SePayQrDetails(
                normalizedCode,
                qrUrl,
                bankName,
                accountNumber,
                accountName
        );
    }
}
