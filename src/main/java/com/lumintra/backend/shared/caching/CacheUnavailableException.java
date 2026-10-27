package com.lumintra.backend.shared.caching;

public class CacheUnavailableException extends RuntimeException {
    public CacheUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}