package com.lumintra.backend.shared.exception.device;

public class WarrantyExpiredException extends RuntimeException {
    public WarrantyExpiredException() {
        super("Warranty for this device has expired.");
    }

    public WarrantyExpiredException(String message) {
        super(message);
    }

    public WarrantyExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
