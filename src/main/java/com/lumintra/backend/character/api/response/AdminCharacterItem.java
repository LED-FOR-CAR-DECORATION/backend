package com.lumintra.backend.character.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Admin character summary item")
public record AdminCharacterItem(
        @Schema(description = "Unique character identifier", example = "10")
        Long characterId,

        @Schema(description = "Display name of the graphic character", example = "Cyber Fox")
        String characterName,

        @Schema(description = "Detailed character description", example = "Mascot pixel cyberpunk")
        String description,

        @Schema(description = "Creator author employee summary")
        AuthorSummary author,

        @Schema(description = "Total number of graphic versions created under this character", example = "3")
        int totalVersions,

        @Schema(description = "Timestamp when character was first created", example = "2026-11-14T08:30:00Z")
        Instant createdAt
) {
    @Schema(description = "Author designer summary")
    public record AuthorSummary(
            @Schema(description = "Employee ID of the creator designer", example = "4")
            Long employeeId,

            @Schema(description = "Full name of the designer", example = "Test Designer")
            String fullName,

            @Schema(description = "Email address of the designer", example = "designer@lumintra.com")
            String email
    ) {
    }
}
