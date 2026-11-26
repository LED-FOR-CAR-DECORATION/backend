package com.lumintra.backend.shared.exception.commission;

public class CommissionOwnershipMismatchException extends RuntimeException {
    public CommissionOwnershipMismatchException(String message) {
        super(message);
    }
}
