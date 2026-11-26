package com.lumintra.backend.iam.api.request;

import com.lumintra.backend.iam.domain.model.UserDeletionReason;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

@Schema(description = "Reason code for deactivating the account")
public record SoftDeleteUserRequest(
        @NotNull @Schema(example = "USER_REQUEST", requiredMode = Schema.RequiredMode.REQUIRED)
                UserDeletionReason reason) {}
