package com.lumintra.backend.device.domain.model;

/**
 * Catalog status of the firmware a device is running, as seen by the OTA check (DEV-10).
 * {@link #UNKNOWN} means the version is not known, or is not a release of the device's model.
 */
public enum OtaVersionStatus {
    ACTIVE,
    SUPERSEDED,
    DEPRECATED,
    UNKNOWN;

    public static OtaVersionStatus of(FirmwareStatus status) {
        return status == null ? UNKNOWN : valueOf(status.name());
    }
}
