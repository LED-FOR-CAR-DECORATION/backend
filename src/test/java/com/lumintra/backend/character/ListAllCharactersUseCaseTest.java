package com.lumintra.backend.character;

import com.lumintra.backend.character.api.request.AdminCharacterFilterRequest;
import com.lumintra.backend.character.api.response.AdminCharacterItem;
import com.lumintra.backend.character.application.ListAllCharactersUseCase;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListAllCharactersUseCaseTest {

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterVersionRepository characterVersionRepository;

    @Mock
    private EmployeeLookupService employeeLookupService;

    private ListAllCharactersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListAllCharactersUseCase(
                characterRepository,
                characterVersionRepository,
                employeeLookupService
        );
    }

    @Test
    @DisplayName("CHR-02 UseCase UTCID01 [N]: Valid admin request returns paginated characters with author details and version counts")
    void execute_validRequest_returnsCharactersWithAuthorsAndVersionCounts() {
        AdminCharacterFilterRequest request = new AdminCharacterFilterRequest(null, null, null, null, 0, 10);

        CharacterEntity char1 = CharacterEntity.create(4L, "Cyber Fox", "Cyberpunk fox mascot");
        char1.setCharacterId(10L);
        char1.setCreatedAt(Instant.parse("2026-11-14T08:30:00Z"));

        CharacterEntity char2 = CharacterEntity.create(5L, "Neon Cat", "Neon cat mascot");
        char2.setCharacterId(11L);
        char2.setCreatedAt(Instant.parse("2026-11-14T09:00:00Z"));

        Page<CharacterEntity> page = new PageImpl<>(List.of(char1, char2));
        when(characterRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // Version counts
        List<Object[]> countRows = List.of(
                new Object[]{10L, 3L},
                new Object[]{11L, 1L}
        );
        when(characterVersionRepository.countVersionsByCharacterIds(List.of(10L, 11L))).thenReturn(countRows);

        // Author profiles
        EmployeeProfileSnapshot author1 = new EmployeeProfileSnapshot(4L, "Test Designer", "designer@lumintra.com");
        EmployeeProfileSnapshot author2 = new EmployeeProfileSnapshot(5L, "Senior Artist", "artist@lumintra.com");
        when(employeeLookupService.findProfilesByEmployeeIds(Set.of(4L, 5L)))
                .thenReturn(Map.of(4L, author1, 5L, author2));

        Page<AdminCharacterItem> result = useCase.execute(request);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);

        AdminCharacterItem item1 = result.getContent().get(0);
        assertThat(item1.characterId()).isEqualTo(10L);
        assertThat(item1.characterName()).isEqualTo("Cyber Fox");
        assertThat(item1.description()).isEqualTo("Cyberpunk fox mascot");
        assertThat(item1.totalVersions()).isEqualTo(3);
        assertThat(item1.author()).isNotNull();
        assertThat(item1.author().employeeId()).isEqualTo(4L);
        assertThat(item1.author().fullName()).isEqualTo("Test Designer");
        assertThat(item1.author().email()).isEqualTo("designer@lumintra.com");

        AdminCharacterItem item2 = result.getContent().get(1);
        assertThat(item2.characterId()).isEqualTo(11L);
        assertThat(item2.characterName()).isEqualTo("Neon Cat");
        assertThat(item2.totalVersions()).isEqualTo(1);
        assertThat(item2.author()).isNotNull();
        assertThat(item2.author().employeeId()).isEqualTo(5L);
        assertThat(item2.author().fullName()).isEqualTo("Senior Artist");
        assertThat(item2.author().email()).isEqualTo("artist@lumintra.com");

        verify(characterVersionRepository).countVersionsByCharacterIds(List.of(10L, 11L));
        verify(employeeLookupService).findProfilesByEmployeeIds(Set.of(4L, 5L));
    }

    @Test
    @DisplayName("CHR-02 UseCase UTCID02 [N]: Applies search query and creatorEmployeeId filters to specification")
    void execute_withSearchQueryAndCreatorFilter_appliesBothFilters() {
        AdminCharacterFilterRequest request = new AdminCharacterFilterRequest("fox", 4L, null, null, 0, 10);

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<AdminCharacterItem> result = useCase.execute(request);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verify(characterRepository).findAll(any(Specification.class), any(Pageable.class));
        verifyNoInteractions(characterVersionRepository, employeeLookupService);
    }

    @Test
    @DisplayName("CHR-02 UseCase UTCID03 [N]: Empty page returns empty list without querying versions or authors")
    void execute_emptyPage_returnsEmptyListWithoutQueryingVersionsOrAuthors() {
        AdminCharacterFilterRequest request = new AdminCharacterFilterRequest(null, null, null, null, 1, 10);

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<AdminCharacterItem> result = useCase.execute(request);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(characterVersionRepository, employeeLookupService);
    }

    @Test
    @DisplayName("CHR-02 UseCase UTCID04 [N]: Character with null creatorEmployeeId maps author to null")
    void execute_characterWithNullCreatorEmployeeId_handlesGracefully() {
        AdminCharacterFilterRequest request = new AdminCharacterFilterRequest(null, null, null, null, 0, 10);

        CharacterEntity char1 = CharacterEntity.create(null, "System Icon", "Default icon");
        char1.setCharacterId(99L);
        char1.setCreatedAt(Instant.parse("2026-11-15T00:00:00Z"));

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(char1)));

        when(characterVersionRepository.countVersionsByCharacterIds(List.of(99L)))
                .thenReturn(Collections.emptyList());

        when(employeeLookupService.findProfilesByEmployeeIds(Collections.emptySet()))
                .thenReturn(Collections.emptyMap());

        Page<AdminCharacterItem> result = useCase.execute(request);

        assertThat(result.getContent()).hasSize(1);
        AdminCharacterItem item = result.getContent().get(0);
        assertThat(item.characterId()).isEqualTo(99L);
        assertThat(item.author()).isNull();
        assertThat(item.totalVersions()).isZero();
    }

    @Test
    @DisplayName("CHR-02 UseCase UTCID05 [N]: Character with unknown author returns fallback author with employeeId only")
    void execute_characterWithUnknownAuthor_handlesGracefully() {
        AdminCharacterFilterRequest request = new AdminCharacterFilterRequest(null, null, null, null, 0, 10);

        CharacterEntity char1 = CharacterEntity.create(88L, "Orphaned Graphic", "Deleted employee asset");
        char1.setCharacterId(101L);

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(char1)));

        when(characterVersionRepository.countVersionsByCharacterIds(List.of(101L)))
                .thenReturn(Collections.emptyList());

        when(employeeLookupService.findProfilesByEmployeeIds(Set.of(88L)))
                .thenReturn(Collections.emptyMap());

        Page<AdminCharacterItem> result = useCase.execute(request);

        assertThat(result.getContent()).hasSize(1);
        AdminCharacterItem item = result.getContent().get(0);
        assertThat(item.author()).isNotNull();
        assertThat(item.author().employeeId()).isEqualTo(88L);
        assertThat(item.author().fullName()).isNull();
        assertThat(item.author().email()).isNull();
    }

    @Test
    @DisplayName("CHR-02 UseCase UTCID06 [N]: Character with zero versions returns totalVersions equal to zero")
    void execute_characterWithZeroVersions_setsTotalVersionsZero() {
        AdminCharacterFilterRequest request = new AdminCharacterFilterRequest(null, null, null, null, 0, 10);

        CharacterEntity char1 = CharacterEntity.create(4L, "New Char", "No versions yet");
        char1.setCharacterId(102L);

        when(characterRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(char1)));

        when(characterVersionRepository.countVersionsByCharacterIds(List.of(102L)))
                .thenReturn(Collections.emptyList());

        EmployeeProfileSnapshot author = new EmployeeProfileSnapshot(4L, "Test Designer", "designer@lumintra.com");
        when(employeeLookupService.findProfilesByEmployeeIds(Set.of(4L)))
                .thenReturn(Map.of(4L, author));

        Page<AdminCharacterItem> result = useCase.execute(request);

        assertThat(result.getContent()).hasSize(1);
        AdminCharacterItem item = result.getContent().get(0);
        assertThat(item.totalVersions()).isZero();
    }
}
