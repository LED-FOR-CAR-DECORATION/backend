package com.lumintra.backend.store.api.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStoreTagRequest(
        @Schema(description = "Unique category tag name", example = "Animals")
                @NotBlank
                @Size(max = 100)
                String name,
        @Schema(description = "Optional category description", example = "Animal art")
                String description) {
    public UpdateStoreTagRequest {
        name = name == null ? null : name.strip();
    }
}
