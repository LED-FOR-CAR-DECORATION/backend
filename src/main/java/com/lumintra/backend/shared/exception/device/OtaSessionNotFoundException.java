package com.lumintra.backend.shared.exception.device;

/**
 * The presented OTA download token is unknown, expired, revoked, or its device is no longer BOUND.
 * Mapped to 404 OTA_SESSION_NOT_FOUND; the cause is deliberately not revealed to the caller.
 */
public class OtaSessionNotFoundException extends RuntimeException {

    public OtaSessionNotFoundException() {
        super("OTA session not found or no longer valid.");
    }
}
