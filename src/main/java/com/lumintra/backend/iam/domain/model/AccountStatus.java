// src/main/java/com/lumintra/backend/iam/domain/model/AccountStatus.java
package com.lumintra.backend.iam.domain.model;

public enum AccountStatus {
    PENDING_VERIFICATION, // Pending OTP email verification
    ACTIVE,     // Fully verified and operational
    SUSPENDED,  // Manually disabled by an Admin
    DELETED     // Soft-deleted account
}