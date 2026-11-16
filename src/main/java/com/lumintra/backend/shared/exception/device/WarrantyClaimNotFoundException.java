package com.lumintra.backend.shared.exception.device;

public class WarrantyClaimNotFoundException extends RuntimeException {
    public WarrantyClaimNotFoundException() {
        super("Warranty claim not found.");
    }

    public WarrantyClaimNotFoundException(String message) {
        super(message);
    }

    public WarrantyClaimNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
