package com.lumintra.backend.shared.exception.device;

public class DuplicateLedModelNameException extends RuntimeException {

    public DuplicateLedModelNameException(String modelName) {
        super("LED model with name '" + modelName + "' already exists.");
    }
}
