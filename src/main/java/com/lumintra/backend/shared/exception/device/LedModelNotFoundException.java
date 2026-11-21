package com.lumintra.backend.shared.exception.device;

public class LedModelNotFoundException extends RuntimeException {

    public LedModelNotFoundException(Long modelId) {
        super("LED model with id " + modelId + " not found.");
    }
}
