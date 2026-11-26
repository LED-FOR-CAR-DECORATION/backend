package com.lumintra.backend.shared.exception.device;

/** The release was already deprecated; deprecation cannot be repeated or undone. Mapped to 409. */
public class FirmwareAlreadyDeprecatedException extends RuntimeException {

    public FirmwareAlreadyDeprecatedException(Long firmwareId) {
        super("Firmware release with id " + firmwareId + " is already deprecated.");
    }
}
