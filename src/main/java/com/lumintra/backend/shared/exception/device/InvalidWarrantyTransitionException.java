package com.lumintra.backend.shared.exception.device;

public class InvalidWarrantyTransitionException extends RuntimeException {
    public InvalidWarrantyTransitionException() {
        super("Invalid warranty claim status transition.");
    }

    public InvalidWarrantyTransitionException(String message) {
        super(message);
    }

    public InvalidWarrantyTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
