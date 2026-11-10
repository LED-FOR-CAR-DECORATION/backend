package com.lumintra.backend.shared.exception.commerce;

public class ProductAlreadyOwnedException extends RuntimeException {
    public ProductAlreadyOwnedException(String message) {
        super(message);
    }
}
