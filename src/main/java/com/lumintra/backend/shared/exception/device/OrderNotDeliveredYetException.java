package com.lumintra.backend.shared.exception.device;

public class OrderNotDeliveredYetException extends RuntimeException {
    public OrderNotDeliveredYetException() {
        super("Order must be DELIVERED or COMPLETED before claiming this device.");
    }

    public OrderNotDeliveredYetException(String message) {
        super(message);
    }

    public OrderNotDeliveredYetException(String message, Throwable cause) {
        super(message, cause);
    }
}
