package com.lumintra.backend.character.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Designer character summary item")
public record DesignerCharacterItem(
        @Schema(description = "Unique character identifier", example = "10")
        Long characterId,

        @Schema(description = "Display name of the graphic character", example = "Cyber Fox")
        String characterName,

        @Schema(description = "Detailed character description", example = "Mascot pixel cyberpunk")
        String description,

        @Schema(description = "Total number of graphic versions created under this character", example = "3")
        int totalVersions,

        @Schema(description = "Current active graphic version summary, or null if no versions exist")
        CurrentVersionSummary currentVersion,

        @Schema(description = "Timestamp when character was first created", example = "2026-11-14T08:30:00Z")
        Instant createdAt
) {
    @Schema(description = "Summary of the active graphic version")
    public record CurrentVersionSummary(
            @Schema(description = "Unique version identifier", example = "25")
            Long versionId,

            @Schema(description = "Sequential version number", example = "3")
            int versionNumber,

            @Schema(description = "Public URL to the preview animated GIF", example = "https://cdn.lumintra.com/characters/cyber-fox-v3.gif")
            String previewGifUrl
    ) {
    }
}
