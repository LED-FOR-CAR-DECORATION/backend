package com.lumintra.backend.shared.exception.character;

public class CharacterNotFoundException extends RuntimeException {

    public CharacterNotFoundException() {
        super("Character not found.");
    }

    public CharacterNotFoundException(String message) {
        super(message);
    }

    public CharacterNotFoundException(Long characterId) {
        super("Character not found with id: " + characterId);
    }

    public CharacterNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
