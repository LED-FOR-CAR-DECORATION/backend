package com.lumintra.backend.shared.exception.commission;

/**
 * Thrown when a reassignment request would not change anything (target already assigned). Mapped to 400.
 */
public class InvalidCommissionReassignmentException extends RuntimeException {

    public InvalidCommissionReassignmentException(String message) {
        super(message);
    }
}
