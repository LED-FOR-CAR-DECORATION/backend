package com.lumintra.backend.firmware.api.request;
import jakarta.validation.constraints.*;
public final class FirmwareRequests {
    private FirmwareRequests() {}
    public record Device(@Pattern(regexp="[0-9A-F]{12}") @NotNull String deviceId, @Positive @NotNull Long ownerAccountId,
                         @NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._-]{0,31}") String hardwareRevision) {}
    public record Session(@NotBlank String currentVersion, @NotBlank @Pattern(regexp="[0-9a-f]{32}") String nonce) {}
}
