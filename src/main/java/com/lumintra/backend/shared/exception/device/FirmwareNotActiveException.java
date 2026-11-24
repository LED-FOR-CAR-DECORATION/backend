package com.lumintra.backend.shared.exception.device;

/** The requested firmware is not the device model's current ACTIVE release. Mapped to 409 FIRMWARE_NOT_ACTIVE. */
public class FirmwareNotActiveException extends RuntimeException {

    public FirmwareNotActiveException(String message) {
        super(message);
    }
}
