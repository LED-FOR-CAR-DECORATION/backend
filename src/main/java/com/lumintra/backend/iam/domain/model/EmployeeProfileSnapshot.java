package com.lumintra.backend.iam.domain.model;

/**
 * Read-only snapshot of an employee's profile and account email for cross-module reads.
 */
public record EmployeeProfileSnapshot(
        Long employeeId,
        String fullName,
        String email
) {
}
