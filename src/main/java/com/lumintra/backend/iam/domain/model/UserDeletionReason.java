package com.lumintra.backend.iam.domain.model;

public enum UserDeletionReason {
    DUPLICATE_ACCOUNT,
    POLICY_VIOLATION,
    USER_REQUEST,
    ADMINISTRATIVE,
    OTHER
}
