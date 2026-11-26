package com.lumintra.backend.character;

import com.lumintra.backend.character.api.request.UpdateCharacterRequest;
import com.lumintra.backend.character.api.response.UpdateCharacterResponse;
import com.lumintra.backend.character.application.UpdateCharacterUseCase;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateCharacterUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private CharacterRepository characterRepository;

    @InjectMocks
    private UpdateCharacterUseCase updateCharacterUseCase;

    @Test
    @DisplayName("CHR-04 [N]: Owner updates character with description successfully")
    void execute_whenValidOwnerWithDescription_shouldUpdateAndReturnResponse() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;
        UpdateCharacterRequest request = new UpdateCharacterRequest("Neon Samurai Remastered", "Bản nâng cấp màu sắc rực rỡ");

        CharacterEntity existing = CharacterEntity.create(10L, "Old Name", "Old Desc");
        existing.setCharacterId(characterId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(existing));
        when(characterRepository.save(any(CharacterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCharacterResponse response = updateCharacterUseCase.execute(subject, characterId, request);

        assertThat(response).isNotNull();
        assertThat(response.characterId()).isEqualTo(12L);
        assertThat(response.characterName()).isEqualTo("Neon Samurai Remastered");
        assertThat(response.description()).isEqualTo("Bản nâng cấp màu sắc rực rỡ");
        assertThat(response.updatedAt()).isNotNull();

        verify(characterRepository).save(existing);
    }

    @Test
    @DisplayName("CHR-04 [N]: Owner updates character without description (null) successfully")
    void execute_whenValidOwnerWithoutDescription_shouldUpdateWithNullDescription() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;
        UpdateCharacterRequest request = new UpdateCharacterRequest("Neon Samurai Remastered", null);

        CharacterEntity existing = CharacterEntity.create(10L, "Old Name", "Old Desc");
        existing.setCharacterId(characterId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(existing));
        when(characterRepository.save(any(CharacterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCharacterResponse response = updateCharacterUseCase.execute(subject, characterId, request);

        assertThat(response).isNotNull();
        assertThat(response.characterName()).isEqualTo("Neon Samurai Remastered");
        assertThat(response.description()).isNull();

        verify(characterRepository).save(existing);
    }

    @Test
    @DisplayName("CHR-04 [A]: Character does not exist -> CharacterNotFoundException")
    void execute_whenCharacterNotFound_shouldThrowCharacterNotFoundException() {
        String subject = "4";
        Long characterId = 999L;
        UpdateCharacterRequest request = new UpdateCharacterRequest("Neon Samurai", null);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(10L);
        when(characterRepository.findById(characterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateCharacterUseCase.execute(subject, characterId, request))
                .isInstanceOf(CharacterNotFoundException.class)
                .hasMessageContaining("999");

        verify(characterRepository, never()).save(any());
    }

    @Test
    @DisplayName("CHR-04 [A]: Caller is not creator of character (BR-FL5-07) -> AccessDeniedException")
    void execute_whenCallerIsNotOwner_shouldThrowAccessDeniedException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long creatorEmployeeId = 99L; // Different designer
        Long characterId = 12L;
        UpdateCharacterRequest request = new UpdateCharacterRequest("Hacked Name", null);

        CharacterEntity existing = CharacterEntity.create(creatorEmployeeId, "Original Name", "Desc");
        existing.setCharacterId(characterId);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> updateCharacterUseCase.execute(subject, characterId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access is denied.");

        verify(characterRepository, never()).save(any());
    }

    @Test
    @DisplayName("CHR-04 [A]: Caller is not an active DESIGNER in DB -> AccessDeniedException")
    void execute_whenCallerIsNotActiveDesigner_shouldThrowAccessDeniedException() {
        String subject = "invalid-or-demoted";
        Long characterId = 12L;
        UpdateCharacterRequest request = new UpdateCharacterRequest("Name", null);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> updateCharacterUseCase.execute(subject, characterId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authorized internal role is required.");

        verifyNoInteractions(characterRepository);
    }
}
