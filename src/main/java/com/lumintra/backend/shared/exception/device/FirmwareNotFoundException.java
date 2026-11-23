package com.lumintra.backend.shared.exception.device;

/** No firmware release with the given id. Mapped to 404 FIRMWARE_NOT_FOUND. */
public class FirmwareNotFoundException extends RuntimeException {

    public FirmwareNotFoundException(Long firmwareId) {
        super("Firmware release with id " + firmwareId + " not found.");
    }
}
