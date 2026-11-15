package com.lumintra.backend.shared.exception.character;

public class CannotDeleteListedCharacterException extends RuntimeException {

    public CannotDeleteListedCharacterException() {
        super("Cannot delete character with versions listed on the Store.");
    }

    public CannotDeleteListedCharacterException(String message) {
        super(message);
    }

    public CannotDeleteListedCharacterException(Long characterId) {
        super("Cannot delete character with id " + characterId + " because one or more versions are listed on the Store.");
    }

    public CannotDeleteListedCharacterException(String message, Throwable cause) {
        super(message, cause);
    }
}
