package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Revision brief proposed by the assigned staff member")
public record PostRevisionCardRequest(
        @Schema(
                        description = "Requested revision details",
                        example = "Make the outline brighter cyan.")
                @NotBlank
                @Size(max = 2000)
                String brief) {
    public PostRevisionCardRequest {
        brief = brief == null ? null : brief.strip();
    }
}
