package com.lumintra.backend.character;

import com.lumintra.backend.character.api.request.CreateCharacterVersionRequest;
import com.lumintra.backend.character.api.response.CreateCharacterVersionResponse;
import com.lumintra.backend.character.application.CreateCharacterVersionUseCase;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.CharacterVersionMapper;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCharacterVersionUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterVersionRepository characterVersionRepository;

    @Spy
    private CharacterVersionMapper characterVersionMapper = new CharacterVersionMapper();

    @InjectMocks
    private CreateCharacterVersionUseCase createCharacterVersionUseCase;

    private CharacterEntity sampleCharacter;

    @BeforeEach
    void setUp() {
        sampleCharacter = CharacterEntity.create(10L, "Cyber Ninja", "High-tech animated ninja");
        sampleCharacter.setCharacterId(12L);
    }

    private CreateCharacterVersionRequest validRequest(Boolean isCurrent) {
        return new CreateCharacterVersionRequest(
                "https://cdn.lumintra.com/characters/samurai-v2.gif",
                16,
                100,
                65536,
                isCurrent
        );
    }

    @Test
    @DisplayName("CHR-07 [N]: First version creation (empty existing) -> versionNumber=1, isCurrent=true, product_id=null")
    void execute_whenFirstVersion_shouldSetVersionOne() {
        String subject = "4";
        Long designerEmployeeId = 10L;
        Long characterId = 12L;
        CreateCharacterVersionRequest request = validRequest(true);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));
        when(characterVersionRepository.findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId))
                .thenReturn(Collections.emptyList());
        when(characterVersionRepository.save(any(CharacterVersionEntity.class)))
                .thenAnswer(invocation -> {
                    CharacterVersionEntity entity = invocation.getArgument(0);
                    entity.setVersionId(101L);
                    return entity;
                });

        CreateCharacterVersionResponse response = createCharacterVersionUseCase.execute(subject, characterId, request);

        assertThat(response.versionId()).isEqualTo(101L);
        assertThat(response.characterId()).isEqualTo(12L);
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.isCurrent()).isTrue();
        assertThat(response.isListedOnStore()).isFalse();

        ArgumentCaptor<CharacterVersionEntity> captor = ArgumentCaptor.forClass(CharacterVersionEntity.class);
        verify(characterVersionRepository).save(captor.capture());
        CharacterVersionEntity saved = captor.getValue();
        assertThat(saved.getVersionNumber()).isEqualTo(1);
        assertThat(saved.getProductId()).isNull();
        assertThat(saved.getLedBinaryUrl()).isNull();
        assertThat(saved.isCurrent()).isTrue();
        assertThat(saved.getFrameCount()).isEqualTo(16);
        assertThat(saved.getFrameDelayMs()).isEqualTo(100);
        assertThat(saved.getByteSize()).isEqualTo(65536);
    }

    @Test
    @DisplayName("CHR-07 [N]: Subsequent version with isCurrent=true -> versionNumber=max+1, resets previous version")
    void execute_whenSubsequentVersionWithIsCurrentTrue_shouldIncrementAndResetPrevious() {
        String subject = "4";
        Long designerEmployeeId = 10L;
        Long characterId = 12L;
        CreateCharacterVersionRequest request = validRequest(true);

        CharacterVersionEntity v1 = CharacterVersionEntity.create(
                sampleCharacter,
                null,
                1,
                "preview1.gif",
                "binary1.bin",
                10,
                120,
                32768,
                true
        );
        v1.setVersionId(101L);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));
        when(characterVersionRepository.findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId))
                .thenReturn(List.of(v1));
        when(characterVersionRepository.save(any(CharacterVersionEntity.class)))
                .thenAnswer(invocation -> {
                    CharacterVersionEntity entity = invocation.getArgument(0);
                    entity.setVersionId(102L);
                    return entity;
                });

        CreateCharacterVersionResponse response = createCharacterVersionUseCase.execute(subject, characterId, request);

        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.isCurrent()).isTrue();
        assertThat(v1.isCurrent()).isFalse(); // Previous version reset to false (AC05)
    }

    @Test
    @DisplayName("CHR-07 [N]: Subsequent version with isCurrent=false -> preserves previous isCurrent=true")
    void execute_whenSubsequentVersionWithIsCurrentFalse_shouldPreservePrevious() {
        String subject = "4";
        Long designerEmployeeId = 10L;
        Long characterId = 12L;
        CreateCharacterVersionRequest request = validRequest(false);

        CharacterVersionEntity v1 = CharacterVersionEntity.create(
                sampleCharacter,
                null,
                1,
                "preview1.gif",
                "binary1.bin",
                10,
                120,
                32768,
                true
        );
        v1.setVersionId(101L);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));
        when(characterVersionRepository.findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId))
                .thenReturn(List.of(v1));
        when(characterVersionRepository.save(any(CharacterVersionEntity.class)))
                .thenAnswer(invocation -> {
                    CharacterVersionEntity entity = invocation.getArgument(0);
                    entity.setVersionId(102L);
                    return entity;
                });

        CreateCharacterVersionResponse response = createCharacterVersionUseCase.execute(subject, characterId, request);

        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.isCurrent()).isFalse();
        assertThat(v1.isCurrent()).isTrue(); // Previous version unchanged
    }

    @Test
    @DisplayName("CHR-07 [N]: Null isCurrent in request defaults to true")
    void execute_whenIsCurrentNull_shouldDefaultToTrue() {
        String subject = "4";
        Long designerEmployeeId = 10L;
        Long characterId = 12L;
        CreateCharacterVersionRequest request = validRequest(null); // Compact constructor defaults to true

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));
        when(characterVersionRepository.findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId))
                .thenReturn(Collections.emptyList());
        when(characterVersionRepository.save(any(CharacterVersionEntity.class)))
                .thenAnswer(invocation -> {
                    CharacterVersionEntity entity = invocation.getArgument(0);
                    entity.setVersionId(101L);
                    return entity;
                });

        CreateCharacterVersionResponse response = createCharacterVersionUseCase.execute(subject, characterId, request);

        assertThat(response.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("CHR-07 [A]: Character does not exist -> throws CharacterNotFoundException")
    void execute_whenCharacterNotFound_shouldThrowCharacterNotFoundException() {
        String subject = "4";
        Long designerEmployeeId = 10L;
        Long characterId = 999L;
        CreateCharacterVersionRequest request = validRequest(true);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createCharacterVersionUseCase.execute(subject, characterId, request))
                .isInstanceOf(CharacterNotFoundException.class)
                .hasMessageContaining("999");

        verifyNoInteractions(characterVersionRepository);
    }

    @Test
    @DisplayName("CHR-07 [A]: Caller is not author of character (BR-FL5-07) -> AccessDeniedException")
    void execute_whenCallerNotOwner_shouldThrowAccessDeniedException() {
        String subject = "4";
        Long designerEmployeeId = 99L; // Different from creator 10L
        Long characterId = 12L;
        CreateCharacterVersionRequest request = validRequest(true);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));

        assertThatThrownBy(() -> createCharacterVersionUseCase.execute(subject, characterId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only create versions for your own characters.");

        verifyNoInteractions(characterVersionRepository);
    }

    @Test
    @DisplayName("CHR-07 [A]: Caller is not an active DESIGNER in IAM -> AccessDeniedException")
    void execute_whenCallerNotActiveDesigner_shouldThrowAccessDeniedException() {
        String subject = "inactive";
        Long characterId = 12L;
        CreateCharacterVersionRequest request = validRequest(true);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenThrow(new AccessDeniedException("Active internal account is required."));

        assertThatThrownBy(() -> createCharacterVersionUseCase.execute(subject, characterId, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Active internal account is required.");

        verifyNoInteractions(characterRepository);
        verifyNoInteractions(characterVersionRepository);
    }
}
