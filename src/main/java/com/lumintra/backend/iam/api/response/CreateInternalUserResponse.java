package com.lumintra.backend.iam.api.response;

import com.lumintra.backend.iam.domain.model.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Created internal account without generated password or credentials")
public record CreateInternalUserResponse(
        @Schema(
                        description = "New account ID",
                        example = "42",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long accountId,
        @Schema(
                        description = "Normalized email",
                        example = "staff@example.com",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String email,
        @Schema(
                        description = "Optional employee full name",
                        example = "Nguyen Van An",
                        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
                String name,
        @Schema(
                        description = "Single assigned role",
                        example = "STAFF",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String role,
        @Schema(
                        description = "Initial account status",
                        example = "ACTIVE",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                AccountStatus status) {}
