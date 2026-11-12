package com.lumintra.backend.shared.exception.store;

public class StoreProductReferenceException extends RuntimeException {
    public enum Reason {
        TAG_NOT_FOUND,
        CHARACTER_VERSION_NOT_FOUND,
        CHARACTER_VERSION_ALREADY_LINKED
    }

    private final Reason reason;

    public StoreProductReferenceException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
