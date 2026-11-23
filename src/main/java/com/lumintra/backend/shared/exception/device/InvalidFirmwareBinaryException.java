package com.lumintra.backend.shared.exception.device;

/** The uploaded file is not a usable ESP32 firmware image (BR-GEN-08). Mapped to 400. */
public class InvalidFirmwareBinaryException extends RuntimeException {

    public InvalidFirmwareBinaryException(String message) {
        super(message);
    }
}
