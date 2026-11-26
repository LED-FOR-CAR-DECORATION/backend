package com.lumintra.backend.commerce.domain.model;

public enum OrderStatus {
    PENDING_PAYMENT,
    PROCESSING,
    PARTIALLY_FULFILLED,
    SHIPPING,
    DELIVERED,
    COMPLETED,
    DELIVERY_FAILED,
    REFUNDED,
    CANCELED
}
