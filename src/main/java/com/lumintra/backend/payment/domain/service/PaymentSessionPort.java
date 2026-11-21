package com.lumintra.backend.payment.domain.service;

import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.domain.model.CancelPaymentSessionCommand;
import com.lumintra.backend.payment.domain.model.CreatePaymentSessionCommand;

/**
 * Inbound port exposed by the payment module for creating and cancelling SePay VietQR payment
 * sessions.
 */
public interface PaymentSessionPort {

    PaymentSessionResponse createSession(CreatePaymentSessionCommand command);

    void cancelSession(CancelPaymentSessionCommand command);
}
