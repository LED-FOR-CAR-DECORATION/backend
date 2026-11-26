package com.lumintra.backend.character.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Details of a graphic character version including Store listing status")
public record CharacterVersionResponse(
        @Schema(description = "Unique identifier of the character version", example = "25")
        Long versionId,

        @Schema(description = "Incremental version sequence number", example = "2")
        int versionNumber,

        @Schema(description = "Public CDN URL for animated GIF preview", example = "https://cdn.lumintra.com/characters/samurai-v2.gif")
        String previewGifUrl,

        @Schema(description = "Total number of animation frames", example = "16")
        Integer frameCount,

        @Schema(description = "Frame delay duration in milliseconds", example = "100")
        Integer frameDelayMs,

        @Schema(description = "Binary payload size in bytes", example = "65536")
        Integer byteSize,

        @Schema(description = "Whether this version is the current active revision", example = "true")
        boolean isCurrent,

        @Schema(description = "Whether this version is published/listed on the Store catalog", example = "false")
        boolean isListedOnStore,

        @Schema(description = "Associated Store product identifier if listed", example = "108")
        Long productId,

        @Schema(description = "Store product publication status if listed", example = "PUBLISHED")
        String productStatus,

        @Schema(description = "Creation timestamp of this version", example = "2026-11-15T02:30:00Z")
        Instant createdAt
) {
}
