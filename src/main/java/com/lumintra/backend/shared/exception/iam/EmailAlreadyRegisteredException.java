package com.lumintra.backend.shared.exception.iam;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("This email address is already registered.");
    }
}
