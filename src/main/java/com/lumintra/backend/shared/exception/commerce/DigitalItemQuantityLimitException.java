package com.lumintra.backend.shared.exception.commerce;

public class DigitalItemQuantityLimitException extends RuntimeException {
    public DigitalItemQuantityLimitException(String message) {
        super(message);
    }
}
