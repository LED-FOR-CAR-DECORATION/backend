package com.lumintra.backend.character.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response payload after successfully creating a character animation version")
public record CreateCharacterVersionResponse(
        @Schema(description = "Unique identifier of the newly created version", example = "25")
        Long versionId,

        @Schema(description = "Identifier of the parent graphic character", example = "12")
        Long characterId,

        @Schema(description = "Auto-incremented sequence number for this version", example = "2")
        int versionNumber,

        @Schema(description = "Public CDN URL for animated GIF preview", example = "https://cdn.lumintra.com/characters/samurai-v2.gif")
        String previewGifUrl,

        @Schema(description = "Whether this version is currently set as the active revision", example = "true")
        boolean isCurrent,

        @Schema(description = "Whether this version is listed on Store (always false upon initial creation)", example = "false")
        boolean isListedOnStore,

        @Schema(description = "Timestamp when this version was created", example = "2026-11-15T02:30:00Z")
        Instant createdAt
) {
}
