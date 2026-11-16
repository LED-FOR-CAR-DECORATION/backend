package com.lumintra.backend.shared.exception.character;

public class CharacterVersionNotFoundException extends RuntimeException {

    public CharacterVersionNotFoundException(Long versionId) {
        super("Character version not found with id: " + versionId);
    }

    public CharacterVersionNotFoundException(Long characterId, Long versionId) {
        super("Character version with id " + versionId + " was not found for character " + characterId);
    }
}
