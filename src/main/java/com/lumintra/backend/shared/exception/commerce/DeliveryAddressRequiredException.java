package com.lumintra.backend.shared.exception.commerce;

public class DeliveryAddressRequiredException extends RuntimeException {
    public DeliveryAddressRequiredException(String message) {
        super(message);
    }
}
