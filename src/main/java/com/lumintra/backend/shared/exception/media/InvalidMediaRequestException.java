package com.lumintra.backend.shared.exception.media;

public class InvalidMediaRequestException extends RuntimeException {
    public InvalidMediaRequestException(String message) {
        super(message);
    }
}
