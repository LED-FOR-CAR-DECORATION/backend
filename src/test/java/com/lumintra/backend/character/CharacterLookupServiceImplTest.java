package com.lumintra.backend.character;

import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;
import com.lumintra.backend.character.infrastructure.persistence.CharacterLookupServiceImpl;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterLookupServiceImplTest {

    @Mock
    private CharacterVersionRepository characterVersionRepository;

    private CharacterLookupServiceImpl characterLookupService;

    @BeforeEach
    void setUp() {
        characterLookupService = new CharacterLookupServiceImpl(characterVersionRepository);
    }

    @Test
    @DisplayName("findDetailByProductId prefers isCurrent version and falls back to latest versionNumber without exposing binary URL")
    void findDetailByProductId_prefersCurrentThenLatestVersion() {
        assertThat(characterLookupService.findDetailByProductId(null)).isEmpty();
        verifyNoInteractions(characterVersionRepository);

        CharacterEntity character = CharacterEntity.create(1L, "Cyber Dragon", "Neon fire dragon");
        character.setCharacterId(10L);

        CharacterVersionEntity current = CharacterVersionEntity.create(
                character, 8L, 2, "https://cdn.lumintra.com/dragon-v2.gif", "https://secret-bucket/dragon.bin", true);
        current.setVersionId(21L);
        current.setFrameCount(24);
        current.setFrameDelayMs(80);

        when(characterVersionRepository.findWithCharacterFirstByProductIdAndIsCurrentTrue(8L))
                .thenReturn(Optional.of(current));

        Optional<CharacterVersionSnapshot> foundCurrent = characterLookupService.findDetailByProductId(8L);
        assertThat(foundCurrent).isPresent();
        assertThat(foundCurrent.get().characterId()).isEqualTo(10L);
        assertThat(foundCurrent.get().characterName()).isEqualTo("Cyber Dragon");
        assertThat(foundCurrent.get().description()).isEqualTo("Neon fire dragon");
        assertThat(foundCurrent.get().versionNumber()).isEqualTo(2);
        assertThat(foundCurrent.get().previewGifUrl()).isEqualTo("https://cdn.lumintra.com/dragon-v2.gif");
        assertThat(foundCurrent.get().frameCount()).isEqualTo(24);
        assertThat(foundCurrent.get().frameDelayMs()).isEqualTo(80);

        CharacterVersionEntity fallback = CharacterVersionEntity.create(
                character, 9L, 3, "https://cdn.lumintra.com/dragon-v3.gif", "https://secret-bucket/dragon3.bin", false);
        fallback.setVersionId(22L);
        when(characterVersionRepository.findWithCharacterFirstByProductIdAndIsCurrentTrue(9L))
                .thenReturn(Optional.empty());
        when(characterVersionRepository.findWithCharacterFirstByProductIdOrderByVersionNumberDesc(9L))
                .thenReturn(Optional.of(fallback));

        Optional<CharacterVersionSnapshot> foundFallback = characterLookupService.findDetailByProductId(9L);
        assertThat(foundFallback).isPresent();
        assertThat(foundFallback.get().versionNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("findDetailsByVersionIds returns batch snapshots keyed by versionId and handles empty inputs")
    void findDetailsByVersionIds_returnsBatchMap() {
        assertThat(characterLookupService.findDetailsByVersionIds(null)).isEmpty();
        assertThat(characterLookupService.findDetailsByVersionIds(List.of())).isEmpty();
        verifyNoInteractions(characterVersionRepository);

        CharacterEntity character = CharacterEntity.create(1L, "Pixel Cat", "Cute cat");
        character.setCharacterId(11L);
        CharacterVersionEntity version = CharacterVersionEntity.create(
                character, 13L, 1, "https://cdn.lumintra.com/cat.gif", "https://secret-bucket/cat.bin", true);
        version.setVersionId(31L);
        version.setFrameCount(12);
        version.setFrameDelayMs(100);

        when(characterVersionRepository.findWithCharacterByVersionIdIn(Set.of(31L)))
                .thenReturn(List.of(version));

        Map<Long, CharacterVersionSnapshot> result = characterLookupService.findDetailsByVersionIds(Set.of(31L));
        assertThat(result).containsOnlyKeys(31L);
        assertThat(result.get(31L).characterName()).isEqualTo("Pixel Cat");
        assertThat(result.get(31L).versionNumber()).isEqualTo(1);
    }
}
