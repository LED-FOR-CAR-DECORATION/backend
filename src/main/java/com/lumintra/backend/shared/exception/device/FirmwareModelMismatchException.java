package com.lumintra.backend.shared.exception.device;

/** The firmware release belongs to a different LED model than the device. Mapped to 409 FIRMWARE_MODEL_MISMATCH. */
public class FirmwareModelMismatchException extends RuntimeException {

    public FirmwareModelMismatchException(String message) {
        super(message);
    }
}
