package com.lumintra.backend.shared.exception.device;

/** The version already exists for the model or is not higher than every existing one. Mapped to 409. */
public class FirmwareVersionConflictException extends RuntimeException {

    public FirmwareVersionConflictException(String message) {
        super(message);
    }
}
