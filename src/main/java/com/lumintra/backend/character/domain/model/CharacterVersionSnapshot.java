package com.lumintra.backend.character.domain.model;

/**
 * Read-only character and version snapshot for store/catalog consumers.
 * Strictly omits ledBinaryUrl to avoid leaking downloadable binary firmware assets.
 */
public record CharacterVersionSnapshot(
        Long versionId,
        Long characterId,
        String characterName,
        String description,
        int versionNumber,
        String previewGifUrl,
        Integer frameCount,
        Integer frameDelayMs
) {}
