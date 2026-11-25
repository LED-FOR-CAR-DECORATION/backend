package com.lumintra.backend.character.api.response;

import com.lumintra.backend.character.domain.model.CharacterType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response payload after successfully updating a graphic character")
public record UpdateCharacterResponse(
        @Schema(description = "Unique identifier of the character", example = "12")
        Long characterId,

        @Schema(description = "Display name of the character", example = "Neon Samurai Remastered")
        String characterName,

        @Schema(description = "Detailed description of the character", example = "Bản nâng cấp màu sắc rực rỡ")
        String description,

        @Schema(description = "Immutable character type classification (STORE or COMMISSION)", example = "STORE")
        CharacterType characterType,

        @Schema(description = "Optional linked commission ID when characterType is COMMISSION", example = "105", nullable = true)
        Long commissionId,

        @Schema(description = "Timestamp when the character was last updated", example = "2026-11-15T02:10:00Z")
        Instant updatedAt
) {
}
