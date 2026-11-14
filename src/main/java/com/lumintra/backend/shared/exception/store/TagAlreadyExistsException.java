package com.lumintra.backend.shared.exception.store;

public class TagAlreadyExistsException extends RuntimeException {
    public TagAlreadyExistsException() {
        super("A category tag with this name already exists.");
    }
}
