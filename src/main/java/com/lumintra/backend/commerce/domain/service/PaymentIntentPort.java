package com.lumintra.backend.commerce.domain.service;

import com.lumintra.backend.commerce.domain.model.PaymentGateway;
import com.lumintra.backend.commerce.domain.model.PaymentIntentResult;

import java.math.BigDecimal;

/**
 * Domain port wrapping payment gateway transaction initialization behind an integration boundary.
 */
public interface PaymentIntentPort {

    PaymentIntentResult initializePaymentIntent(Long orderId, BigDecimal amount, PaymentGateway gateway);
}
