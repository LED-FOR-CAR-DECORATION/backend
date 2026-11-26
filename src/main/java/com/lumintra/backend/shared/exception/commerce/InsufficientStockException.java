package com.lumintra.backend.shared.exception.commerce;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
