package com.lumintra.backend.shared.exception.device;

/** No customer device with the given id. Mapped to 404 DEVICE_NOT_FOUND. */
public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(String message) {
        super(message);
    }
}
