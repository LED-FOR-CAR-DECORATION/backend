package com.lumintra.backend.shared.exception.iam;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Email này đã được đăng ký.");
    }
}
