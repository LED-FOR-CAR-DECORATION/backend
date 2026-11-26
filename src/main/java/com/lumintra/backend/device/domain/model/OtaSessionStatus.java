package com.lumintra.backend.device.domain.model;

/**
 * Lifecycle of a one-time OTA download session (DEV-21). A session is usable while {@link #ISSUED}
 * and before its {@code expiresAt}; a newer session for the same device {@link #REVOKED revokes} it,
 * and the installed-firmware report (DEV-10B) marks it {@link #COMPLETED}.
 */
public enum OtaSessionStatus {
    ISSUED,
    COMPLETED,
    REVOKED
}
