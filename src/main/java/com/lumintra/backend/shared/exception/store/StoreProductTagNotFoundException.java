package com.lumintra.backend.shared.exception.store;

public class StoreProductTagNotFoundException extends RuntimeException {
    public StoreProductTagNotFoundException() {
        super("One or more tags were not found.");
    }
}
