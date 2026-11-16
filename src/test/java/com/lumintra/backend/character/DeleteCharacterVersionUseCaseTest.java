package com.lumintra.backend.character;

import com.lumintra.backend.character.application.DeleteCharacterVersionUseCase;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import com.lumintra.backend.shared.exception.character.CharacterVersionAlreadyLinkedToProductException;
import com.lumintra.backend.shared.exception.character.CharacterVersionNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteCharacterVersionUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterVersionRepository characterVersionRepository;

    @InjectMocks
    private DeleteCharacterVersionUseCase deleteCharacterVersionUseCase;

    @Test
    @DisplayName("CHR-08 [N]: Owner deletes unlisted draft version successfully")
    void execute_whenValidUnlistedVersion_shouldDeleteVersion() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;
        Long versionId = 25L;

        CharacterEntity character = CharacterEntity.create(10L, "Draft Mascot", "Description");
        character.setCharacterId(characterId);

        CharacterVersionEntity version = CharacterVersionEntity.create(
                character,
                null, // unlisted
                2,
                "https://cdn.lumintra.com/characters/v2.gif",
                16,
                100,
                65536,
                true
        );
        version.setVersionId(versionId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(characterVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        deleteCharacterVersionUseCase.execute(subject, characterId, versionId);

        verify(characterVersionRepository).delete(version);
    }

    @Test
    @DisplayName("CHR-08 [A]: Character does not exist -> CharacterNotFoundException")
    void execute_whenCharacterNotFound_shouldThrowCharacterNotFoundException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 999L;
        Long versionId = 25L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteCharacterVersionUseCase.execute(subject, characterId, versionId))
                .isInstanceOf(CharacterNotFoundException.class)
                .hasMessage("Character not found with id: 999");

        verify(characterVersionRepository, never()).findById(any());
        verify(characterVersionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("CHR-08 [A]: Caller is not creator of character (BR-FL5-07) -> AccessDeniedException")
    void execute_whenNotOwner_shouldThrowAccessDeniedException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long otherEmployeeId = 99L;
        Long characterId = 12L;
        Long versionId = 25L;

        CharacterEntity character = CharacterEntity.create(otherEmployeeId, "Other Mascot", "Description");
        character.setCharacterId(characterId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));

        assertThatThrownBy(() -> deleteCharacterVersionUseCase.execute(subject, characterId, versionId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only delete versions of characters you own.");

        verify(characterVersionRepository, never()).findById(any());
        verify(characterVersionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("CHR-08 [A]: Caller is not an active DESIGNER in IAM -> AccessDeniedException")
    void execute_whenNotActiveDesignerInIam_shouldThrowAccessDeniedException() {
        String subject = "4";
        Long characterId = 12L;
        Long versionId = 25L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenThrow(new AccessDeniedException("Active internal account is required."));

        assertThatThrownBy(() -> deleteCharacterVersionUseCase.execute(subject, characterId, versionId))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(characterRepository);
        verifyNoInteractions(characterVersionRepository);
    }

    @Test
    @DisplayName("CHR-08 [A]: Character version does not exist -> CharacterVersionNotFoundException")
    void execute_whenVersionNotFound_shouldThrowCharacterVersionNotFoundException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;
        Long versionId = 999L;

        CharacterEntity character = CharacterEntity.create(10L, "Draft Mascot", "Description");
        character.setCharacterId(characterId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(characterVersionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteCharacterVersionUseCase.execute(subject, characterId, versionId))
                .isInstanceOf(CharacterVersionNotFoundException.class)
                .hasMessage("Character version with id 999 was not found for character 12");

        verify(characterVersionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("CHR-08 [A]: Character version belongs to another character -> CharacterVersionNotFoundException")
    void execute_whenVersionBelongsToAnotherCharacter_shouldThrowCharacterVersionNotFoundException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;
        Long anotherCharacterId = 88L;
        Long versionId = 25L;

        CharacterEntity character = CharacterEntity.create(10L, "Draft Mascot", "Description");
        character.setCharacterId(characterId);

        CharacterEntity anotherCharacter = CharacterEntity.create(10L, "Another Mascot", "Description");
        anotherCharacter.setCharacterId(anotherCharacterId);

        CharacterVersionEntity version = CharacterVersionEntity.create(
                anotherCharacter,
                null,
                1,
                "https://cdn.lumintra.com/characters/v1.gif",
                16,
                100,
                65536,
                true
        );
        version.setVersionId(versionId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(characterVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> deleteCharacterVersionUseCase.execute(subject, characterId, versionId))
                .isInstanceOf(CharacterVersionNotFoundException.class)
                .hasMessage("Character version with id 25 was not found for character 12");

        verify(characterVersionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("CHR-08 [A]: Version listed on Store (BR-FL5-09) -> CharacterVersionAlreadyLinkedToProductException")
    void execute_whenVersionListedOnStore_shouldThrowCharacterVersionAlreadyLinkedToProductException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;
        Long versionId = 25L;
        Long productId = 108L;

        CharacterEntity character = CharacterEntity.create(10L, "Draft Mascot", "Description");
        character.setCharacterId(characterId);

        CharacterVersionEntity version = CharacterVersionEntity.create(
                character,
                productId, // listed on Store!
                1,
                "https://cdn.lumintra.com/characters/v1.gif",
                16,
                100,
                65536,
                true
        );
        version.setVersionId(versionId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(characterVersionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> deleteCharacterVersionUseCase.execute(subject, characterId, versionId))
                .isInstanceOf(CharacterVersionAlreadyLinkedToProductException.class)
                .hasMessage("Cannot delete character version with id 25 because it is listed on the Store (productId: 108).");

        verify(characterVersionRepository, never()).delete(any());
    }
}
