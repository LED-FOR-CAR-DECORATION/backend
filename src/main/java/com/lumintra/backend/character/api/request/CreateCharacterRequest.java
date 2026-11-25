package com.lumintra.backend.character.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lumintra.backend.character.domain.model.CharacterType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
        String description,

        @Schema(
                description = "Purpose of this character: STORE for commercial catalog sale, COMMISSION for custom commission work",
                allowableValues = {"STORE", "COMMISSION"},
                defaultValue = "STORE",
                example = "STORE"
        )
        CharacterType characterType,

        @Schema(
                description = "Optional linked commission ID (only valid when characterType is COMMISSION)",
                example = "105",
                nullable = true
        )
        @Positive(message = "commissionId must be positive")
        Long commissionId
) {
    public CreateCharacterRequest {
        characterName = characterName != null ? characterName.strip() : null;
        description = description != null ? description.strip() : null;
        if (description != null && description.isEmpty()) {
            description = null;
        }
        if (characterType == null) {
            characterType = CharacterType.STORE;
        }
    }

    @JsonIgnore
    @AssertTrue(message = "commissionId is only allowed when characterType is COMMISSION")
    public boolean isCommissionIdValidForType() {
        return commissionId == null || characterType == CharacterType.COMMISSION;
    }
}
