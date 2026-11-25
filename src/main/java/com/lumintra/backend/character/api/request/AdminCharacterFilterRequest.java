package com.lumintra.backend.character.api.request;

import com.lumintra.backend.character.domain.model.CharacterType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@Schema(description = "Query parameters for admin management character catalog")
public record AdminCharacterFilterRequest(
        @Schema(
                description = "Optional search query substring matching character name or description (case-insensitive)",
                example = "cyber"
        )
        String query,

        @Schema(
                description = "Optional filter by creator designer employee ID",
                example = "4"
        )
        @Positive(message = "creatorEmployeeId must be positive")
        Long creatorEmployeeId,

        @Schema(
                description = "Optional filter by character type (STORE or COMMISSION)",
                allowableValues = {"STORE", "COMMISSION"},
                example = "STORE"
        )
        CharacterType characterType,

        @Schema(
                description = "Optional filter by linked commission ID",
                example = "105"
        )
        @Positive(message = "commissionId must be positive")
        Long commissionId,

        @Schema(
                description = "0-based page index",
                defaultValue = "0",
                example = "0"
        )
        @Min(value = 0, message = "page must be greater than or equal to 0")
        Integer page,

        @Schema(
                description = "Page size (number of records per page, between 1 and 100)",
                defaultValue = "10",
                example = "10"
        )
        @Min(value = 1, message = "size must be greater than or equal to 1")
        @Max(value = 100, message = "size must be less than or equal to 100")
        Integer size
) {
    public AdminCharacterFilterRequest {
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 10;
        }
        query = query != null ? query.strip() : null;
        if (query != null && query.isEmpty()) {
            query = null;
        }
    }
}
