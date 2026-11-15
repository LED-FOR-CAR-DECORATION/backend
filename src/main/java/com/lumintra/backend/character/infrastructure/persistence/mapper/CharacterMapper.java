package com.lumintra.backend.character.infrastructure.persistence.mapper;

import com.lumintra.backend.character.api.response.CreateCharacterResponse;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;

public final class CharacterMapper {

    private CharacterMapper() {
    }

    public static CreateCharacterResponse toCreateResponse(CharacterEntity entity) {
        return new CreateCharacterResponse(
                entity.getCharacterId(),
                entity.getCharacterName(),
                entity.getDescription(),
                entity.getCreatorEmployeeId(),
                entity.getCreatedAt()
        );
    }
}
