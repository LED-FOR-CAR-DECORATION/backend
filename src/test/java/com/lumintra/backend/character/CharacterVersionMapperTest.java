package com.lumintra.backend.character;

import com.lumintra.backend.character.api.response.CharacterVersionResponse;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.CharacterVersionMapper;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterVersionMapperTest {

    private final CharacterVersionMapper mapper = new CharacterVersionMapper();

    @Test
    @DisplayName("CHR-06 [N]: Map version without productId -> isListedOnStore false, productStatus null")
    void toResponse_whenUnlistedVersion_shouldMapFields() {
        CharacterEntity character = CharacterEntity.create(10L, "Samurai", "Desc");
        CharacterVersionEntity entity = CharacterVersionEntity.create(
                character,
                null,
                2,
                "preview.gif",
                "binary.bin",
                true
        );
        entity.setVersionId(25L);
        entity.setFrameCount(16);
        entity.setFrameDelayMs(100);
        entity.setByteSize(65536);

        CharacterVersionResponse response = mapper.toResponse(entity, Map.of());

        assertThat(response.versionId()).isEqualTo(25L);
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.previewGifUrl()).isEqualTo("preview.gif");
        assertThat(response.frameCount()).isEqualTo(16);
        assertThat(response.frameDelayMs()).isEqualTo(100);
        assertThat(response.byteSize()).isEqualTo(65536);
        assertThat(response.isCurrent()).isTrue();
        assertThat(response.isListedOnStore()).isFalse();
        assertThat(response.productId()).isNull();
        assertThat(response.productStatus()).isNull();
    }

    @Test
    @DisplayName("CHR-06 [N]: Map version with listed productId -> isListedOnStore true, productStatus PUBLISHED")
    void toResponse_whenListedVersion_shouldMapStoreStatus() {
        CharacterEntity character = CharacterEntity.create(10L, "Samurai", "Desc");
        CharacterVersionEntity entity = CharacterVersionEntity.create(
                character,
                108L,
                1,
                "preview1.gif",
                "binary1.bin",
                false
        );
        entity.setVersionId(24L);

        ProductSnapshot snapshot = new ProductSnapshot(
                108L,
                "Product 108",
                ProductType.CHARACTER,
                "thumb.png",
                BigDecimal.TEN,
                ProductStatus.PUBLISHED
        );

        CharacterVersionResponse response = mapper.toResponse(entity, Map.of(108L, snapshot));

        assertThat(response.isListedOnStore()).isTrue();
        assertThat(response.productId()).isEqualTo(108L);
        assertThat(response.productStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("CHR-07 [N]: Map entity to CreateCharacterVersionResponse")
    void toCreateResponse_whenNewVersion_shouldMapFields() {
        CharacterEntity character = CharacterEntity.create(10L, "Samurai", "Desc");
        character.setCharacterId(12L);
        CharacterVersionEntity entity = CharacterVersionEntity.create(
                character,
                null,
                2,
                "preview2.gif",
                "binary2.bin",
                16,
                100,
                65536,
                true
        );
        entity.setVersionId(25L);

        var response = mapper.toCreateResponse(entity);

        assertThat(response.versionId()).isEqualTo(25L);
        assertThat(response.characterId()).isEqualTo(12L);
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.previewGifUrl()).isEqualTo("preview2.gif");
        assertThat(response.isCurrent()).isTrue();
        assertThat(response.isListedOnStore()).isFalse();
    }
}
