package com.lumintra.backend.shared.exception.payment;

public class PaymentSessionNotCancellableException extends RuntimeException {
    public PaymentSessionNotCancellableException(String message) {
        super(message);
    }
}
