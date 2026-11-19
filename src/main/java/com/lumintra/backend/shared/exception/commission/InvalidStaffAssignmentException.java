package com.lumintra.backend.shared.exception.commission;

/**
 * Thrown when a target staff employee ID is invalid, inactive, or not a member of the STAFF role.
 */
public class InvalidStaffAssignmentException extends RuntimeException {

    public InvalidStaffAssignmentException(String message) {
        super(message);
    }
}
