package com.lumintra.backend.shared.exception.notification;

public class InternalUserEmailUnavailableException extends RuntimeException {
    public InternalUserEmailUnavailableException(Throwable cause) {
        super("Internal account email provider is unavailable", cause);
    }
}
