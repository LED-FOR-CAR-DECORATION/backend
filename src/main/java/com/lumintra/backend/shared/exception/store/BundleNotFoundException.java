package com.lumintra.backend.shared.exception.store;

public class BundleNotFoundException extends RuntimeException {
    public BundleNotFoundException() {
        super("Bundle not found.");
    }
}
