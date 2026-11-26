package com.lumintra.backend.shared.exception.device;

public class LedInstanceNotFoundException extends RuntimeException {
    public LedInstanceNotFoundException() {
        super("LED instance not found.");
    }

    public LedInstanceNotFoundException(String message) {
        super(message);
    }

    public LedInstanceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
