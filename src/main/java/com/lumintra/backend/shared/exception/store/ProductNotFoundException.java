package com.lumintra.backend.shared.exception.store;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException() {
        super("Product not found.");
    }

    public ProductNotFoundException(String message) {
        super(message);
    }

    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
