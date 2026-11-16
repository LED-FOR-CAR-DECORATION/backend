package com.lumintra.backend.shared.exception.device;

public class WarrantyNotActiveException extends RuntimeException {
    public WarrantyNotActiveException() {
        super("Warranty for this device has not been activated.");
    }

    public WarrantyNotActiveException(String message) {
        super(message);
    }

    public WarrantyNotActiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
