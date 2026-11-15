package com.lumintra.backend.character.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for updating an existing graphic character")
public record UpdateCharacterRequest(
        @Schema(description = "Display name of the graphic character", example = "Neon Samurai Remastered")
        @NotBlank(message = "characterName must not be blank")
        @Size(max = 255, message = "characterName must not exceed 255 characters")
        String characterName,

        @Schema(description = "Optional detailed description of the graphic character", example = "Bản nâng cấp màu sắc rực rỡ")
        String description
) {
    public UpdateCharacterRequest {
        characterName = characterName != null ? characterName.trim() : null;
        description = (description != null && !description.isBlank()) ? description.trim() : null;
    }
}
