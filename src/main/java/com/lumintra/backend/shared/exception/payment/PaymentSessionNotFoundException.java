package com.lumintra.backend.shared.exception.payment;

public class PaymentSessionNotFoundException extends RuntimeException {
    public PaymentSessionNotFoundException(String message) {
        super(message);
    }
}
