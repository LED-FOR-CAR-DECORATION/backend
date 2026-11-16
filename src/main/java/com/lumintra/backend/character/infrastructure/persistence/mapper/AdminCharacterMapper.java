package com.lumintra.backend.character.infrastructure.persistence.mapper;

import com.lumintra.backend.character.api.response.AdminCharacterItem;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;

public final class AdminCharacterMapper {

    private AdminCharacterMapper() {
    }

    public static AdminCharacterItem toItem(
            CharacterEntity character,
            EmployeeProfileSnapshot authorProfile,
            int totalVersions) {

        AdminCharacterItem.AuthorSummary authorSummary = null;
        if (authorProfile != null) {
            authorSummary = new AdminCharacterItem.AuthorSummary(
                    authorProfile.employeeId(),
                    authorProfile.fullName(),
                    authorProfile.email()
            );
        } else if (character.getCreatorEmployeeId() != null) {
            authorSummary = new AdminCharacterItem.AuthorSummary(
                    character.getCreatorEmployeeId(),
                    null,
                    null
            );
        }

        return new AdminCharacterItem(
                character.getCharacterId(),
                character.getCharacterName(),
                character.getDescription(),
                authorSummary,
                totalVersions,
                character.getCreatedAt()
        );
    }
}
