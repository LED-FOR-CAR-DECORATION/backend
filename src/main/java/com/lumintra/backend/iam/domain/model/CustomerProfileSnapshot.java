package com.lumintra.backend.iam.domain.model;

/**
 * Read-only snapshot of a customer's profile and account email for cross-module reads.
 */
public record CustomerProfileSnapshot(
        Long customerId,
        String fullName,
        String phone,
        String email
) {}
