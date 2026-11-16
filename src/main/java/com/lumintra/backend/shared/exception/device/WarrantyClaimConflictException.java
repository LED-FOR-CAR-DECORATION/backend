package com.lumintra.backend.shared.exception.device;

public class WarrantyClaimConflictException extends RuntimeException {
    public WarrantyClaimConflictException() {
        super("An active warranty claim already exists for this device.");
    }

    public WarrantyClaimConflictException(String message) {
        super(message);
    }

    public WarrantyClaimConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
