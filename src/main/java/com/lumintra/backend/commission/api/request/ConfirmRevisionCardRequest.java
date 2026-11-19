package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Revision brief card selected by the customer")
public record ConfirmRevisionCardRequest(
        @NotNull
                @Positive
                @Schema(
                        description = "Message ID of the latest revision brief card",
                        example = "402",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Long messageId) {}
