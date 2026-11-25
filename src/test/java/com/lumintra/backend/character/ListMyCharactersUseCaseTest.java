package com.lumintra.backend.character;

import com.lumintra.backend.character.api.request.DesignerCharacterFilterRequest;
import com.lumintra.backend.character.api.response.DesignerCharacterItem;
import com.lumintra.backend.character.application.ListMyCharactersUseCase;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListMyCharactersUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterVersionRepository characterVersionRepository;

    private ListMyCharactersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListMyCharactersUseCase(
                internalEmployeeAccessService,
                characterRepository,
                characterVersionRepository
        );
    }

    @Test
    @DisplayName("CHR-01 UseCase UTCID01 [N]: Valid designer returns paginated characters with active versions and total count")
    void execute_validDesigner_returnsPaginatedCharactersWithVersions() {
        String subject = "5";
        Long designerEmployeeId = 42L;
        DesignerCharacterFilterRequest request = new DesignerCharacterFilterRequest(null, null, null, 0, 10);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);

        CharacterEntity char1 = CharacterEntity.create(designerEmployeeId, "Cyber Fox", "Cyberpunk fox mascot");
        char1.setCharacterId(10L);
        char1.setCreatedAt(Instant.parse("2026-11-14T08:30:00Z"));

        CharacterEntity char2 = CharacterEntity.create(designerEmployeeId, "Neon Cat", "Neon cat mascot");
        char2.setCharacterId(11L);
        char2.setCreatedAt(Instant.parse("2026-11-14T09:00:00Z"));

        Page<CharacterEntity> page = new PageImpl<>(List.of(char1, char2));
        when(characterRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        CharacterVersionEntity v1 = CharacterVersionEntity.create(char1, 100L, 1, "https://cdn/v1.gif", "https://cdn/v1.bin", false);
        v1.setVersionId(21L);
        CharacterVersionEntity v2 = CharacterVersionEntity.create(char1, 100L, 2, "https://cdn/v2.gif", "https://cdn/v2.bin", true);
        v2.setVersionId(22L);
        CharacterVersionEntity v3 = CharacterVersionEntity.create(char2, 101L, 1, "https://cdn/cat-v1.gif", "https://cdn/cat-v1.bin", true);
        v3.setVersionId(31L);

        when(characterVersionRepository.findByCharacter_CharacterIdInOrderByVersionNumberDesc(List.of(10L, 11L)))
                .thenReturn(List.of(v2, v1, v3));

        Page<DesignerCharacterItem> result = useCase.execute(subject, request);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);

        DesignerCharacterItem item1 = result.getContent().get(0);
        assertThat(item1.characterId()).isEqualTo(10L);
        assertThat(item1.characterName()).isEqualTo("Cyber Fox");
        assertThat(item1.description()).isEqualTo("Cyberpunk fox mascot");
        assertThat(item1.totalVersions()).isEqualTo(2);
        assertThat(item1.currentVersion()).isNotNull();
        assertThat(item1.currentVersion().versionId()).isEqualTo(22L);
        assertThat(item1.currentVersion().versionNumber()).isEqualTo(2);
        assertThat(item1.currentVersion().previewGifUrl()).isEqualTo("https://cdn/v2.gif");

        DesignerCharacterItem item2 = result.getContent().get(1);
        assertThat(item2.characterId()).isEqualTo(11L);
        assertThat(item2.characterName()).isEqualTo("Neon Cat");
        assertThat(item2.totalVersions()).isEqualTo(1);
        assertThat(item2.currentVersion()).isNotNull();
        assertThat(item2.currentVersion().versionId()).isEqualTo(31L);
        assertThat(item2.currentVersion().versionNumber()).isEqualTo(1);
        assertThat(item2.currentVersion().previewGifUrl()).isEqualTo("https://cdn/cat-v1.gif");

        verify(characterVersionRepository).findByCharacter_CharacterIdInOrderByVersionNumberDesc(List.of(10L, 11L));
    }

    @Test
    @DisplayName("CHR-01 UseCase UTCID02 [N]: Passes keyword query to specification for filtering")
    void execute_withSearchQuery_appliesFilter() {
        String subject = "5";
        Long designerEmployeeId = 42L;
        DesignerCharacterFilterRequest request = new DesignerCharacterFilterRequest("fox", null, null, 0, 10);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<DesignerCharacterItem> result = useCase.execute(subject, request);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(characterRepository).findAll(any(Specification.class), any(Pageable.class));
        verifyNoInteractions(characterVersionRepository);
    }

    @Test
    @DisplayName("CHR-01 UseCase UTCID03 [N]: Empty page returns empty list without querying version repository")
    void execute_emptyPage_returnsEmptyListWithoutFetchingVersions() {
        String subject = "5";
        Long designerEmployeeId = 42L;
        DesignerCharacterFilterRequest request = new DesignerCharacterFilterRequest(null, null, null, 1, 10);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<DesignerCharacterItem> result = useCase.execute(subject, request);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(characterVersionRepository);
    }

    @Test
    @DisplayName("CHR-01 UseCase UTCID04 [N]: Character without versions returns totalVersions=0 and currentVersion=null")
    void execute_characterWithoutVersions_returnsZeroVersionsAndNullCurrentVersion() {
        String subject = "5";
        Long designerEmployeeId = 42L;
        DesignerCharacterFilterRequest request = new DesignerCharacterFilterRequest(null, null, null, 0, 10);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);

        CharacterEntity char1 = CharacterEntity.create(designerEmployeeId, "Empty Char", "No versions yet");
        char1.setCharacterId(99L);
        char1.setCreatedAt(Instant.parse("2026-11-15T01:00:00Z"));

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(char1)));

        when(characterVersionRepository.findByCharacter_CharacterIdInOrderByVersionNumberDesc(List.of(99L)))
                .thenReturn(Collections.emptyList());

        Page<DesignerCharacterItem> result = useCase.execute(subject, request);

        assertThat(result.getContent()).hasSize(1);
        DesignerCharacterItem item = result.getContent().get(0);
        assertThat(item.characterId()).isEqualTo(99L);
        assertThat(item.totalVersions()).isZero();
        assertThat(item.currentVersion()).isNull();
    }

    @Test
    @DisplayName("CHR-01 UseCase UTCID05 [N]: Character with versions where none is marked current falls back to first version")
    void execute_characterWithVersionsNoneCurrent_fallsBackToFirst() {
        String subject = "5";
        Long designerEmployeeId = 42L;
        DesignerCharacterFilterRequest request = new DesignerCharacterFilterRequest(null, null, null, 0, 10);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(designerEmployeeId);

        CharacterEntity char1 = CharacterEntity.create(designerEmployeeId, "Fallback Char", "None current");
        char1.setCharacterId(50L);

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(char1)));

        CharacterVersionEntity v1 = CharacterVersionEntity.create(char1, 100L, 2, "https://cdn/v2.gif", "https://cdn/v2.bin", false);
        v1.setVersionId(88L);

        when(characterVersionRepository.findByCharacter_CharacterIdInOrderByVersionNumberDesc(List.of(50L)))
                .thenReturn(List.of(v1));

        Page<DesignerCharacterItem> result = useCase.execute(subject, request);

        assertThat(result.getContent()).hasSize(1);
        DesignerCharacterItem item = result.getContent().get(0);
        assertThat(item.totalVersions()).isEqualTo(1);
        assertThat(item.currentVersion()).isNotNull();
        assertThat(item.currentVersion().versionId()).isEqualTo(88L);
        assertThat(item.currentVersion().versionNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("CHR-01 UseCase UTCID06 [A]: Non-designer or inactive employee throws AccessDeniedException")
    void execute_nonDesignerOrInactive_throwsAccessDeniedException() {
        String subject = "10";
        DesignerCharacterFilterRequest request = new DesignerCharacterFilterRequest(null, null, null, 0, 10);

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> useCase.execute(subject, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authorized internal role is required.");

        verifyNoInteractions(characterRepository, characterVersionRepository);
    }
}
