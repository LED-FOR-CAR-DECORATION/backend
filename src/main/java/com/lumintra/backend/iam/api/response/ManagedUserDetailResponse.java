package com.lumintra.backend.iam.api.response;

import com.lumintra.backend.iam.domain.model.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(description = "Internal user profile and account state, excluding credentials")
public record ManagedUserDetailResponse(
        @Schema(
                        description = "Account ID",
                        example = "42",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long accountId,
        @Schema(
                        description = "Employee full name",
                        example = "Nguyen Van An",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String name,
        @Schema(
                        description = "Account email",
                        example = "staff@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String email,
        @Schema(
                        description = "Employee phone",
                        example = "0901234567",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String phone,
        @Schema(
                        description = "Profile avatar, falling back to the account avatar",
                        example = "https://example.com/avatar.png",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String avatar,
        @Schema(
                        description = "Assigned database role name without ROLE_ prefix",
                        example = "STAFF",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String role,
        @Schema(
                        description = "Account lifecycle status",
                        example = "SUSPENDED",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                AccountStatus status,
        @Schema(
                        description = "Account creation time in UTC",
                        example = "2026-11-01T12:00:00Z",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Instant createdAt,
        @Schema(
                        description = "Employee profile last modification time in UTC",
                        example = "2026-11-10T12:00:00Z",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                Instant updatedAt,
        @Schema(
                        description =
                                "Password-login lockout state, independent of account lifecycle"
                                        + " status",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                LockoutState lockout) {

    @Schema(description = "Current password-login lockout information")
    public record LockoutState(
            @Schema(
                            description =
                                    "True only when lockoutUntil is strictly after the response"
                                            + " snapshot time",
                            example = "true",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    boolean locked,
            @Schema(
                            description =
                                    "Persisted lockout deadline; may be null or already expired",
                            example = "2026-11-10T12:15:00Z",
                            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                    Instant lockoutUntil,
            @Schema(
                            description = "Persisted consecutive failed password-login attempts",
                            example = "5",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    int failedAttempts) {}
}
