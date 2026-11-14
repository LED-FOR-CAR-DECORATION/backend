package com.lumintra.backend.shared.exception.store;

public class PlanCodeAlreadyExistsException extends RuntimeException {
    public PlanCodeAlreadyExistsException() {
        super("A subscription plan with this code already exists.");
    }
}
