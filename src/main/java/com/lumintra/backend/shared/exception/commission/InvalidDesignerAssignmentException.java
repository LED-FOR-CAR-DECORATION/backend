package com.lumintra.backend.shared.exception.commission;

/**
 * Thrown when an assigned designer employee ID is invalid, inactive, or not a member of the DESIGNER role.
 */
public class InvalidDesignerAssignmentException extends RuntimeException {

    public InvalidDesignerAssignmentException(String message) {
        super(message);
    }
}
