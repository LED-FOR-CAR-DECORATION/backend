package com.lumintra.backend.character.infrastructure.persistence.mapper;

import com.lumintra.backend.character.api.response.CharacterVersionResponse;
import com.lumintra.backend.character.api.response.CreateCharacterVersionResponse;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CharacterVersionMapper {

    public CharacterVersionResponse toResponse(CharacterVersionEntity entity, Map<Long, ProductSnapshot> productSnapshots) {
        Long productId = entity.getProductId();
        boolean isListed = productId != null;
        String productStatus = null;
        if (isListed && productSnapshots != null) {
            ProductSnapshot snapshot = productSnapshots.get(productId);
            if (snapshot != null && snapshot.status() != null) {
                productStatus = snapshot.status().name();
            }
        }

        return new CharacterVersionResponse(
                entity.getVersionId(),
                entity.getVersionNumber(),
                entity.getPreviewGifUrl(),
                entity.getFrameCount(),
                entity.getFrameDelayMs(),
                entity.getByteSize(),
                entity.isCurrent(),
                isListed,
                productId,
                productStatus,
                entity.getCreatedAt()
        );
    }

    public com.lumintra.backend.character.api.response.CreateCharacterVersionResponse toCreateResponse(CharacterVersionEntity entity) {
        Long characterId = entity.getCharacter() != null ? entity.getCharacter().getCharacterId() : null;
        return new com.lumintra.backend.character.api.response.CreateCharacterVersionResponse(
                entity.getVersionId(),
                characterId,
                entity.getVersionNumber(),
                entity.getPreviewGifUrl(),
                entity.isCurrent(),
                entity.getProductId() != null,
                entity.getCreatedAt()
        );
    }
}
