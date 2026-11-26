package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.infrastructure.persistence.LedModelLookupServiceImpl;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
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
class LedModelLookupServiceImplTest {

    @Mock
    private LedModelRepository ledModelRepository;

    private LedModelLookupServiceImpl ledModelLookupService;

    @BeforeEach
    void setUp() {
        ledModelLookupService = new LedModelLookupServiceImpl(ledModelRepository);
    }

    @Test
    @DisplayName("findByModelId returns LedModelSnapshot with unmodifiable specifications and handles null id")
    void findByModelId_returnsSnapshotOrEmpty() {
        assertThat(ledModelLookupService.findByModelId(null)).isEmpty();
        verifyNoInteractions(ledModelRepository);

        LedModelEntity entity = LedModelEntity.create(5L, "Matrix 64x64", Map.of("dimensions", "64x64"), 12L);
        when(ledModelRepository.findById(5L)).thenReturn(Optional.of(entity));

        Optional<LedModelSnapshot> snapshot = ledModelLookupService.findByModelId(5L);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().modelId()).isEqualTo(5L);
        assertThat(snapshot.get().modelName()).isEqualTo("Matrix 64x64");
        assertThat(snapshot.get().spec()).containsEntry("dimensions", "64x64");
        assertThat(snapshot.get().stockQuantity()).isEqualTo(12L);
    }

    @Test
    @DisplayName("findByModelIds maps results keyed by modelId and skips DB on empty input")
    void findByModelIds_returnsBatchMapAndGuardsEmptyInput() {
        assertThat(ledModelLookupService.findByModelIds(null)).isEmpty();
        assertThat(ledModelLookupService.findByModelIds(List.of())).isEmpty();
        verifyNoInteractions(ledModelRepository);

        LedModelEntity first = LedModelEntity.create(5L, "Matrix 64x64", Map.of("dimensions", "64x64"), 12L);
        LedModelEntity second = LedModelEntity.create(6L, "Matrix 32x32", null, null);
        when(ledModelRepository.findAllById(Set.of(5L, 6L))).thenReturn(List.of(first, second));

        Map<Long, LedModelSnapshot> result = ledModelLookupService.findByModelIds(Set.of(5L, 6L));
        assertThat(result).containsOnlyKeys(5L, 6L);
        assertThat(result.get(5L).stockQuantity()).isEqualTo(12L);
        assertThat(result.get(6L).spec()).isEmpty();
        assertThat(result.get(6L).stockQuantity()).isZero();
    }
}
