package com.lumintra.backend.device.domain.model;

/**
 * Verified and canonicalized factory QR payload fields extracted during device claim.
 */
public record VerifiedDeviceQrPayload(
        int version,
        String serialNumber,
        String macAddress,
        String signature
) {}
