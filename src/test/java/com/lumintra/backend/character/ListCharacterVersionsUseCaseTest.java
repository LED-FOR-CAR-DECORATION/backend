package com.lumintra.backend.character;

import com.lumintra.backend.character.api.response.CharacterVersionResponse;
import com.lumintra.backend.character.application.ListCharacterVersionsUseCase;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.CharacterVersionMapper;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
class ListCharacterVersionsUseCaseTest {

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterVersionRepository characterVersionRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Spy
    private CharacterVersionMapper characterVersionMapper = new CharacterVersionMapper();

    @InjectMocks
    private ListCharacterVersionsUseCase listCharacterVersionsUseCase;

    private CharacterEntity sampleCharacter;

    @BeforeEach
    void setUp() {
        sampleCharacter = CharacterEntity.create(10L, "Cyber Ninja", "High-tech animated ninja");
        sampleCharacter.setCharacterId(12L);
    }

    @Test
    @DisplayName("CHR-06 [N]: DESIGNER retrieves own character versions with Store status successfully")
    void execute_whenDesignerInspectsOwnCharacter_shouldReturnVersions() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;

        CharacterVersionEntity v2 = CharacterVersionEntity.create(
                sampleCharacter,
                null,
                2,
                "https://cdn.lumintra.com/v2.gif",
                "binary/v2.bin",
                true
        );
        v2.setVersionId(202L);
        v2.setFrameCount(16);
        v2.setFrameDelayMs(100);
        v2.setByteSize(65536);

        CharacterVersionEntity v1 = CharacterVersionEntity.create(
                sampleCharacter,
                108L,
                1,
                "https://cdn.lumintra.com/v1.gif",
                "binary/v1.bin",
                false
        );
        v1.setVersionId(201L);
        v1.setFrameCount(8);
        v1.setFrameDelayMs(120);
        v1.setByteSize(32768);

        List<CharacterVersionEntity> versions = List.of(v2, v1);

        ProductSnapshot snapshot = new ProductSnapshot(
                108L,
                "Ninja Standalone",
                ProductType.CHARACTER,
                "thumb.png",
                BigDecimal.valueOf(19.99),
                ProductStatus.PUBLISHED
        );

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));
        when(characterVersionRepository.findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId))
                .thenReturn(versions);
        when(productLookupService.findSnapshotsByIds(Set.of(108L)))
                .thenReturn(Map.of(108L, snapshot));

        List<CharacterVersionResponse> result = listCharacterVersionsUseCase.execute(subject, false, characterId);

        assertThat(result).hasSize(2);

        CharacterVersionResponse res2 = result.get(0);
        assertThat(res2.versionId()).isEqualTo(202L);
        assertThat(res2.versionNumber()).isEqualTo(2);
        assertThat(res2.isCurrent()).isTrue();
        assertThat(res2.isListedOnStore()).isFalse();
        assertThat(res2.productId()).isNull();
        assertThat(res2.productStatus()).isNull();

        CharacterVersionResponse res1 = result.get(1);
        assertThat(res1.versionId()).isEqualTo(201L);
        assertThat(res1.versionNumber()).isEqualTo(1);
        assertThat(res1.isCurrent()).isFalse();
        assertThat(res1.isListedOnStore()).isTrue();
        assertThat(res1.productId()).isEqualTo(108L);
        assertThat(res1.productStatus()).isEqualTo("PUBLISHED");

        verify(characterVersionRepository).findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId);
    }

    @Test
    @DisplayName("CHR-06 [N]: MANAGER inspects foreign character -> bypasses ownership check and succeeds")
    void execute_whenManagerInspectsForeignCharacter_shouldBypassOwnership() {
        String subject = "88";
        Long managerEmployeeId = 999L; // Different from creator 10L
        Long characterId = 12L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("MANAGER", "ADMIN"))))
                .thenReturn(managerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));
        when(characterVersionRepository.findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId))
                .thenReturn(Collections.emptyList());

        List<CharacterVersionResponse> result = listCharacterVersionsUseCase.execute(subject, true, characterId);

        assertThat(result).isEmpty();
        verifyNoInteractions(productLookupService);
    }

    @Test
    @DisplayName("CHR-06 [N]: ADMIN inspects foreign character -> bypasses ownership check and succeeds")
    void execute_whenAdminInspectsForeignCharacter_shouldBypassOwnership() {
        String subject = "1";
        Long adminEmployeeId = 1L; // Different from creator 10L
        Long characterId = 12L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("MANAGER", "ADMIN"))))
                .thenReturn(adminEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));
        when(characterVersionRepository.findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId))
                .thenReturn(Collections.emptyList());

        List<CharacterVersionResponse> result = listCharacterVersionsUseCase.execute(subject, true, characterId);

        assertThat(result).isEmpty();
        verifyNoInteractions(productLookupService);
    }

    @Test
    @DisplayName("CHR-06 [N]: Character has no versions -> returns empty list")
    void execute_whenCharacterHasNoVersions_shouldReturnEmptyList() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 12L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));
        when(characterVersionRepository.findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId))
                .thenReturn(Collections.emptyList());

        List<CharacterVersionResponse> result = listCharacterVersionsUseCase.execute(subject, false, characterId);

        assertThat(result).isEmpty();
        verifyNoInteractions(productLookupService);
    }

    @Test
    @DisplayName("CHR-06 [A]: Character does not exist -> throws CharacterNotFoundException")
    void execute_whenCharacterNotFound_shouldThrowCharacterNotFoundException() {
        String subject = "4";
        Long callerEmployeeId = 10L;
        Long characterId = 999L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listCharacterVersionsUseCase.execute(subject, false, characterId))
                .isInstanceOf(CharacterNotFoundException.class)
                .hasMessageContaining("999");

        verifyNoInteractions(characterVersionRepository);
        verifyNoInteractions(productLookupService);
    }

    @Test
    @DisplayName("CHR-06 [A]: DESIGNER inspects character of another designer (BR-FL5-07) -> AccessDeniedException")
    void execute_whenDesignerNotOwner_shouldThrowAccessDeniedException() {
        String subject = "4";
        Long callerEmployeeId = 25L; // Not creator 10L
        Long characterId = 12L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenReturn(callerEmployeeId);
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(sampleCharacter));

        assertThatThrownBy(() -> listCharacterVersionsUseCase.execute(subject, false, characterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only inspect versions of characters created by your account.");

        verifyNoInteractions(characterVersionRepository);
        verifyNoInteractions(productLookupService);
    }

    @Test
    @DisplayName("CHR-06 [A]: Caller is not an active employee in IAM -> AccessDeniedException")
    void execute_whenCallerNotActiveEmployee_shouldThrowAccessDeniedException() {
        String subject = "inactive";
        Long characterId = 12L;

        when(internalEmployeeAccessService.requireActiveEmployeeId(eq(subject), eq(Set.of("DESIGNER"))))
                .thenThrow(new AccessDeniedException("Active internal account is required."));

        assertThatThrownBy(() -> listCharacterVersionsUseCase.execute(subject, false, characterId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Active internal account is required.");

        verifyNoInteractions(characterRepository);
        verifyNoInteractions(characterVersionRepository);
        verifyNoInteractions(productLookupService);
    }
}
