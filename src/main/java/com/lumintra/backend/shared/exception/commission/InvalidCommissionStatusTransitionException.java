package com.lumintra.backend.shared.exception.commission;

/**
 * Thrown when an illegal state transition is attempted on a commission request lifecycle.
 */
public class InvalidCommissionStatusTransitionException extends RuntimeException {

    public InvalidCommissionStatusTransitionException(String message) {
        super(message);
    }
}
