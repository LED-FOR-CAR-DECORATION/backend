package com.lumintra.backend.character;

import com.lumintra.backend.character.api.request.CreateCharacterRequest;
import com.lumintra.backend.character.api.response.CreateCharacterResponse;
import com.lumintra.backend.character.application.CreateCharacterUseCase;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateCharacterUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private CharacterRepository characterRepository;

    private CreateCharacterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateCharacterUseCase(internalEmployeeAccessService, characterRepository);
    }

    @Test
    @DisplayName("CHR-03 UseCase UTCID01 [N]: Authenticated designer creates character successfully")
    void execute_validRequest_createsAndReturnsCharacter() {
        String subject = "42";
        Long designerEmployeeId = 4L;
        CreateCharacterRequest request = new CreateCharacterRequest("Neon Samurai", "Chiến binh samurai phong cách neon retro 8-bit");

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);

        when(characterRepository.save(any(CharacterEntity.class))).thenAnswer(invocation -> {
            CharacterEntity entity = invocation.getArgument(0);
            entity.setCharacterId(12L);
            entity.setCreatedAt(Instant.parse("2026-11-15T02:00:00Z"));
            return entity;
        });

        CreateCharacterResponse response = useCase.execute(subject, request);

        assertThat(response).isNotNull();
        assertThat(response.characterId()).isEqualTo(12L);
        assertThat(response.characterName()).isEqualTo("Neon Samurai");
        assertThat(response.description()).isEqualTo("Chiến binh samurai phong cách neon retro 8-bit");
        assertThat(response.creatorEmployeeId()).isEqualTo(4L);
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-11-15T02:00:00Z"));

        ArgumentCaptor<CharacterEntity> captor = ArgumentCaptor.forClass(CharacterEntity.class);
        verify(characterRepository).save(captor.capture());
        CharacterEntity captured = captor.getValue();
        assertThat(captured.getCreatorEmployeeId()).isEqualTo(designerEmployeeId);
        assertThat(captured.getCharacterName()).isEqualTo("Neon Samurai");
        assertThat(captured.getDescription()).isEqualTo("Chiến binh samurai phong cách neon retro 8-bit");
    }

    @Test
    @DisplayName("CHR-03 UseCase UTCID02 [N]: Authenticated designer creates character without description")
    void execute_validRequestWithoutDescription_createsWithNullDescription() {
        String subject = "42";
        Long designerEmployeeId = 4L;
        CreateCharacterRequest request = new CreateCharacterRequest("Cyber Ninja", null);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);

        when(characterRepository.save(any(CharacterEntity.class))).thenAnswer(invocation -> {
            CharacterEntity entity = invocation.getArgument(0);
            entity.setCharacterId(13L);
            entity.setCreatedAt(Instant.parse("2026-11-15T03:00:00Z"));
            return entity;
        });

        CreateCharacterResponse response = useCase.execute(subject, request);

        assertThat(response.characterId()).isEqualTo(13L);
        assertThat(response.characterName()).isEqualTo("Cyber Ninja");
        assertThat(response.description()).isNull();
        assertThat(response.creatorEmployeeId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("CHR-03 UseCase UTCID03 [A]: Non-designer or inactive employee throws AccessDeniedException")
    void execute_nonDesignerOrInactive_throwsAccessDeniedException() {
        String subject = "10";
        CreateCharacterRequest request = new CreateCharacterRequest("Unauthorized Mascot", "Should fail");

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> useCase.execute(subject, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authorized internal role is required.");

        verifyNoInteractions(characterRepository);
    }
}
