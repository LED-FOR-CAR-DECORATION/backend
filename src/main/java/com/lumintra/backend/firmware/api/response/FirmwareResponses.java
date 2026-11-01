package com.lumintra.backend.firmware.api.response;
import java.time.Instant;
public final class FirmwareResponses {
    private FirmwareResponses() {}
    public record Release(String id, String version, String hardwareRevision, String sha256, int size, boolean deprecated, boolean active, Instant createdAt) {}
    public record Check(boolean updateAvailable, Release release) {}
    public record Session(String token, String version, Instant expiresAt) {}
    public record Manifest(String deviceId, String nonce, String version, String hardwareRevision, String sha256, int size) {}
    public record Device(String deviceId, Long ownerAccountId, String hardwareRevision) {}
    public record Binary(byte[] bytes) {}
}
