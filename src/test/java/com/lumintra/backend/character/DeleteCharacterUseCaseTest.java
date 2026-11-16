package com.lumintra.backend.character;

import com.lumintra.backend.character.application.DeleteCharacterUseCase;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CannotDeleteListedCharacterException;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
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
class DeleteCharacterUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterVersionRepository characterVersionRepository;

    @InjectMocks
    private DeleteCharacterUseCase deleteCharacterUseCase;

    @Test
    @DisplayName("CHR-05 [N]: Owner deletes character without any versions successfully")
    void execute_whenValidDraftWithoutVersions_shouldDeleteCharacter() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;

        CharacterEntity character = CharacterEntity.create(10L, "Draft Mascot", "Description");
        character.setCharacterId(characterId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(characterVersionRepository.findByCharacter_CharacterId(characterId)).thenReturn(Collections.emptyList());

        deleteCharacterUseCase.execute(subject, characterId);

        verify(characterVersionRepository, never()).deleteAll(any());
        verify(characterRepository).delete(character);
    }

    @Test
    @DisplayName("CHR-05 [N]: Owner deletes character with unlisted versions successfully (cascade delete)")
    void execute_whenValidDraftWithUnlistedVersions_shouldCascadeDeleteVersionsAndCharacter() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;

        CharacterEntity character = CharacterEntity.create(10L, "Draft Mascot", "Description");
        character.setCharacterId(characterId);

        CharacterVersionEntity v1 = CharacterVersionEntity.create(character, null, 1, "preview1.gif", "binary1.bin", false);
        CharacterVersionEntity v2 = CharacterVersionEntity.create(character, null, 2, "preview2.gif", "binary2.bin", true);
        List<CharacterVersionEntity> versions = List.of(v1, v2);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(characterVersionRepository.findByCharacter_CharacterId(characterId)).thenReturn(versions);

        deleteCharacterUseCase.execute(subject, characterId);

        verify(characterVersionRepository).deleteAll(versions);
        verify(characterRepository).delete(character);
    }

    @Test
    @DisplayName("CHR-05 [A]: Character does not exist -> CharacterNotFoundException")
    void execute_whenCharacterNotFound_shouldThrowCharacterNotFoundException() {
        String subject = "4";
        Long characterId = 999L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(10L);
        when(characterRepository.findById(characterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deleteCharacterUseCase.execute(subject, characterId))
                .isInstanceOf(CharacterNotFoundException.class)
                .hasMessageContaining("999");

        verifyNoInteractions(characterVersionRepository);
        verify(characterRepository, never()).delete(any(CharacterEntity.class));
    }

    @Test
    @DisplayName("CHR-05 [A]: Caller is not creator of character (BR-FL5-07) -> AccessDeniedException")
    void execute_whenCallerIsNotOwner_shouldThrowAccessDeniedException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long creatorEmployeeId = 99L; // Different designer
        Long characterId = 12L;

        CharacterEntity character = CharacterEntity.create(creatorEmployeeId, "Other Mascot", "Desc");
        character.setCharacterId(characterId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));

        assertThatThrownBy(() -> deleteCharacterUseCase.execute(subject, characterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access is denied.");

        verifyNoInteractions(characterVersionRepository);
        verify(characterRepository, never()).delete(any(CharacterEntity.class));
    }

    @Test
    @DisplayName("CHR-05 [A]: Caller is not an active DESIGNER in DB -> AccessDeniedException")
    void execute_whenCallerIsNotActiveDesigner_shouldThrowAccessDeniedException() {
        String subject = "demoted-or-inactive";
        Long characterId = 12L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> deleteCharacterUseCase.execute(subject, characterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authorized internal role is required.");

        verifyNoInteractions(characterRepository);
        verifyNoInteractions(characterVersionRepository);
    }

    @Test
    @DisplayName("CHR-05 [A]: Character has a version listed on Store (BR-FL5-09) -> CannotDeleteListedCharacterException")
    void execute_whenVersionIsListedOnStore_shouldThrowCannotDeleteListedCharacterException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;

        CharacterEntity character = CharacterEntity.create(10L, "Commercial Mascot", "Desc");
        character.setCharacterId(characterId);

        CharacterVersionEntity v1 = CharacterVersionEntity.create(character, null, 1, "preview1.gif", "binary1.bin", false);
        CharacterVersionEntity v2 = CharacterVersionEntity.create(character, 501L, 2, "preview2.gif", "binary2.bin", true); // Listed with productId=501
        List<CharacterVersionEntity> versions = List.of(v1, v2);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(characterVersionRepository.findByCharacter_CharacterId(characterId)).thenReturn(versions);

        assertThatThrownBy(() -> deleteCharacterUseCase.execute(subject, characterId))
                .isInstanceOf(CannotDeleteListedCharacterException.class)
                .hasMessageContaining("12");

        verify(characterVersionRepository, never()).deleteAll(any());
        verify(characterRepository, never()).delete(any(CharacterEntity.class));
    }
}
