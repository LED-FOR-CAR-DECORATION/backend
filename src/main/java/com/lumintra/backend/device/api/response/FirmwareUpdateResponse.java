package com.lumintra.backend.device.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record FirmwareUpdateResponse(
        @Schema(description = "Whether a newer compatible active version exists")
                boolean updateAvailable,
        @Schema(example = "1.0.0") String currentVersion,
        @Schema(
                        description =
                                "Active compatible version, or null when no release is active",
                        example = "1.2.0")
                String latestVersion) {}
