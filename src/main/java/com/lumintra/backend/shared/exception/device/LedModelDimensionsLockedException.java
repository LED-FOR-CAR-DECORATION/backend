package com.lumintra.backend.shared.exception.device;

public class LedModelDimensionsLockedException extends RuntimeException {

    public LedModelDimensionsLockedException(Long modelId) {
        super("Cannot modify canvas dimensions for LED model with id " + modelId
                + ": physical LED instances have already been provisioned.");
    }
}
