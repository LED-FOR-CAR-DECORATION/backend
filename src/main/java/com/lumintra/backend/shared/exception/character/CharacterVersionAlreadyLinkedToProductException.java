package com.lumintra.backend.shared.exception.character;

public class CharacterVersionAlreadyLinkedToProductException extends RuntimeException {

    public CharacterVersionAlreadyLinkedToProductException(Long versionId, Long productId) {
        super("Cannot delete character version with id " + versionId + " because it is listed on the Store (productId: " + productId + ").");
    }

    public CharacterVersionAlreadyLinkedToProductException(Long versionId) {
        super("Cannot delete character version with id " + versionId + " because it is listed on the Store.");
    }
}
