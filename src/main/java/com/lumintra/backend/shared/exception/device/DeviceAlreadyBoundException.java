package com.lumintra.backend.shared.exception.device;

public class DeviceAlreadyBoundException extends RuntimeException {
    public DeviceAlreadyBoundException() {
        super("Device is already bound to a customer account.");
    }

    public DeviceAlreadyBoundException(String message) {
        super(message);
    }

    public DeviceAlreadyBoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
