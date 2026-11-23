package com.lumintra.backend.shared.exception.device;

/** The device exists and is owned but is not in BOUND status. Mapped to 409 DEVICE_NOT_BOUND. */
public class DeviceNotBoundException extends RuntimeException {

    public DeviceNotBoundException(String message) {
        super(message);
    }
}
