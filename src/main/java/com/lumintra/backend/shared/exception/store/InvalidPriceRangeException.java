package com.lumintra.backend.shared.exception.store;

public class InvalidPriceRangeException extends RuntimeException {
    public InvalidPriceRangeException() {
        super("Minimum price cannot be greater than maximum price");
    }

    public InvalidPriceRangeException(String message) {
        super(message);
    }
}
