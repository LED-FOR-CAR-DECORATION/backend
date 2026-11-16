package com.lumintra.backend.character.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload to create a new graphic character catalog entry")
public record CreateCharacterRequest(
        @Schema(
                description = "Display name of the graphic character",
                example = "Neon Samurai",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "characterName must not be blank")
        @Size(max = 255, message = "characterName must not exceed 255 characters")
        String characterName,

        @Schema(
                description = "Optional detailed character description",
                example = "Chiến binh samurai phong cách neon retro 8-bit"
        )
        String description
) {
    public CreateCharacterRequest {
        characterName = characterName != null ? characterName.strip() : null;
        description = description != null ? description.strip() : null;
        if (description != null && description.isEmpty()) {
            description = null;
        }
    }
}
