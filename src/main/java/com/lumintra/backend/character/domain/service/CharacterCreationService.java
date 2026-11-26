package com.lumintra.backend.character.domain.service;

/**
 * Domain boundary for provisioning character catalog entries linked to store products.
 */
public interface CharacterCreationService {

    long createForProduct(
            long productId,
            String characterName,
            String description,
            int versionNumber,
            String previewGifUrl,
            String ledBinaryUrl,
            Integer frameCount,
            Integer frameDelayMs,
            Integer byteSize);
}
