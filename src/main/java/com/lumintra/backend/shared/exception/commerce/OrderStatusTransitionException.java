package com.lumintra.backend.shared.exception.commerce;

public class OrderStatusTransitionException extends RuntimeException {

    public OrderStatusTransitionException(String message) {
        super(message);
    }
}
