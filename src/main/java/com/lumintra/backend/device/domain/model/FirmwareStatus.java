package com.lumintra.backend.device.domain.model;

/**
 * Lifecycle of a firmware release. Each LED model has at most one {@link #ACTIVE} release;
 * uploading a newer one moves the previous release to {@link #SUPERSEDED}, and a faulty release
 * is {@link #DEPRECATED} (never deleted, BR-FL4-05).
 */
public enum FirmwareStatus {
    ACTIVE,
    SUPERSEDED,
    DEPRECATED
}
