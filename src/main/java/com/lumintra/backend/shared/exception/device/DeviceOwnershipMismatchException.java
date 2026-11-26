package com.lumintra.backend.shared.exception.device;

public class DeviceOwnershipMismatchException extends RuntimeException {
    public DeviceOwnershipMismatchException() {
        super("This device does not belong to an order owned by your account.");
    }

    public DeviceOwnershipMismatchException(String message) {
        super(message);
    }

    public DeviceOwnershipMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
