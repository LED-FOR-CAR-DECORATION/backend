package com.lumintra.backend.shared.exception.iam;

public class InvalidManagedUserRoleException extends RuntimeException {
    public InvalidManagedUserRoleException() {
        super("Select an existing internal role.");
    }
}
