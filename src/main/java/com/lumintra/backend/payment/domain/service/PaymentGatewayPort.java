package com.lumintra.backend.payment.domain.service;

import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.model.SePayQrDetails;

import java.math.BigDecimal;

/**
 * Outbound port for generating SePay transfer codes and dynamic VietQR URLs.
 */
public interface PaymentGatewayPort {

    String generatePaymentCode(PaymentTargetType targetType, Long targetId);

    SePayQrDetails buildQrDetails(String paymentCode, BigDecimal amount);
}
