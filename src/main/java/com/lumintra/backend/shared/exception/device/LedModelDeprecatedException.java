package com.lumintra.backend.shared.exception.device;

public class LedModelDeprecatedException extends RuntimeException {

    public LedModelDeprecatedException(Long modelId) {
        super("LED model " + modelId + " is deprecated and cannot receive new stock instances.");
    }
}
