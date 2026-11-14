package com.lumintra.backend.shared.exception.device;

public class InvalidDeviceQrException extends RuntimeException {
    public InvalidDeviceQrException() {
        super("Invalid or tampered device QR payload.");
    }

    public InvalidDeviceQrException(String message) {
        super(message);
    }

    public InvalidDeviceQrException(String message, Throwable cause) {
        super(message, cause);
    }
}
