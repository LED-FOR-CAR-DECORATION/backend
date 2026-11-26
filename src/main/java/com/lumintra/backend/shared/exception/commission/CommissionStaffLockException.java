package com.lumintra.backend.shared.exception.commission;

/**
 * Thrown when a staff member attempts to approve or modify a commission ticket
 * that is already locked/assigned to another staff member.
 */
public class CommissionStaffLockException extends RuntimeException {

    public CommissionStaffLockException(String message) {
        super(message);
    }
}
