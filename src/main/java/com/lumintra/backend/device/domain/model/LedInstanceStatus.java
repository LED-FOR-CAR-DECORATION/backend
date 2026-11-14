package com.lumintra.backend.device.domain.model;

/**
 * Physical lifecycle state of an individual ESP32 LED hardware instance.
 */
public enum LedInstanceStatus {
    IN_STOCK,
    IN_TRANSIT,
    BOUND,
    RETURNED,
    DECOMMISSIONED
}
