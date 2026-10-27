package com.lumintra.backend.iam.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The newly created Customer account. Sign in separately to receive JWT tokens.")
public record RegisterResponse(
        @Schema(example = "123") Long userId,
        @Schema(example = "customer@example.com") String email,
        @Schema(example = "John Doe") String fullName
) {}
