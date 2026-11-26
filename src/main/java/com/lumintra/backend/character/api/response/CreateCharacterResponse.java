package com.lumintra.backend.character.api.response;

import com.lumintra.backend.character.domain.model.CharacterType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Created character payload")
public record CreateCharacterResponse(
        @Schema(description = "Unique character identifier", example = "12")
        Long characterId,

        @Schema(description = "Display name of the graphic character", example = "Neon Samurai")
        String characterName,

        @Schema(description = "Detailed character description", example = "Chiến binh samurai phong cách neon retro 8-bit")
        String description,

        @Schema(description = "Character type classification (STORE or COMMISSION)", example = "STORE")
        CharacterType characterType,

        @Schema(description = "Optional linked commission ID when characterType is COMMISSION", example = "105", nullable = true)
        Long commissionId,

        @Schema(description = "Employee ID of the creator designer", example = "4")
        Long creatorEmployeeId,

        @Schema(description = "Timestamp when character was created", example = "2026-11-15T02:00:00Z")
        Instant createdAt
) {
}
