package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.UpdateLedModelRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.DuplicateLedModelNameException;
import com.lumintra.backend.shared.exception.device.LedModelDimensionsLockedException;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateLedModelUseCaseTest {

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    private LedModelMapper ledModelMapper;
    private UpdateLedModelUseCase useCase;

    @BeforeEach
    void setUp() {
        ledModelMapper = new LedModelMapper();
        useCase = new UpdateLedModelUseCase(ledModelRepository, ledInstanceRepository, ledModelMapper);
    }

    private LedModelEntity createSampleEntity(Long id, String name, int width, int height, long stock, LedModelStatus status) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("widthPx", width);
        spec.put("heightPx", height);
        spec.put("attributes", List.of(
                Map.of("label", "Voltage", "value", "5V USB-C")
        ));
        return LedModelEntity.createCatalogModel(id, null, name, width, height, spec, stock, status);
    }

    @Test
    @DisplayName("DEV-03 UTCID01 [N]: Update model details without existing instances allows dimension and stock change")
    void execute_validUpdateWithoutInstances_updatesSuccessfully() {
        Long modelId = 1L;
        LedModelEntity existing = createSampleEntity(modelId, "Matrix P4 32x32", 32, 32, 10L, LedModelStatus.ACTIVE);

        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Matrix P4 64x64 Upgraded",
                25L,
                new UpdateLedModelRequest.LedSpecRequest(
                        64,
                        64,
                        List.of(
                                new UpdateLedModelRequest.SpecAttributeRequest("Voltage", "5V USB-C PD"),
                                new UpdateLedModelRequest.SpecAttributeRequest("Pitch", "P3")
                        )
                )
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(existing));
        when(ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot("Matrix P4 64x64 Upgraded", modelId)).thenReturn(false);
        when(ledInstanceRepository.existsByModelId(modelId)).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedModelListResponse.LedModelItem result = useCase.execute(modelId, request);

        assertThat(result).isNotNull();
        assertThat(result.modelId()).isEqualTo(modelId);
        assertThat(result.modelName()).isEqualTo("Matrix P4 64x64 Upgraded");
        assertThat(result.stockQuantity()).isEqualTo(25L);
        assertThat(result.status()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(result.spec().widthPx()).isEqualTo(64);
        assertThat(result.spec().heightPx()).isEqualTo(64);
        assertThat(result.spec().attributes()).hasSize(2);
        assertThat(result.spec().attributes().get(0).label()).isEqualTo("Voltage");
        assertThat(result.spec().attributes().get(0).value()).isEqualTo("5V USB-C PD");
        assertThat(result.spec().attributes().get(1).label()).isEqualTo("Pitch");
        assertThat(result.spec().attributes().get(1).value()).isEqualTo("P3");

        ArgumentCaptor<LedModelEntity> captor = ArgumentCaptor.forClass(LedModelEntity.class);
        verify(ledModelRepository).save(captor.capture());
        LedModelEntity saved = captor.getValue();
        assertThat(saved.getWidthPx()).isEqualTo(64);
        assertThat(saved.getHeightPx()).isEqualTo(64);
        assertThat(saved.getStockQuantity()).isEqualTo(25L);
        assertThat(saved.getStatus()).isEqualTo(LedModelStatus.ACTIVE);
    }

    @Test
    @DisplayName("DEV-03 UTCID02 [A]: Changing canvas dimensions when instances exist throws LedModelDimensionsLockedException (409)")
    void execute_dimensionsLockedWhenInstancesExist_throwsDimensionsLockedException() {
        Long modelId = 2L;
        LedModelEntity existing = createSampleEntity(modelId, "Matrix P4 64x64", 64, 64, 15L, LedModelStatus.ACTIVE);

        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Matrix P4 64x64 New Revision",
                20L,
                new UpdateLedModelRequest.LedSpecRequest(
                        128, // Modified width!
                        64,
                        List.of()
                )
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(existing));
        when(ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot("Matrix P4 64x64 New Revision", modelId)).thenReturn(false);
        when(ledInstanceRepository.existsByModelId(modelId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(modelId, request))
                .isInstanceOf(LedModelDimensionsLockedException.class)
                .hasMessageContaining("Cannot modify canvas dimensions for LED model with id 2");

        verify(ledInstanceRepository).existsByModelId(modelId);
    }

    @Test
    @DisplayName("DEV-03 UTCID03 [N]: Keeping identical canvas dimensions when instances exist allows other field updates")
    void execute_sameDimensionsWhenInstancesExist_allowsOtherFieldUpdates() {
        Long modelId = 3L;
        LedModelEntity existing = createSampleEntity(modelId, "Matrix P4 64x64", 64, 64, 15L, LedModelStatus.ACTIVE);

        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Matrix P4 64x64 Refresh",
                30L,
                new UpdateLedModelRequest.LedSpecRequest(
                        64, // Same width
                        64, // Same height
                        List.of(new UpdateLedModelRequest.SpecAttributeRequest("Brightness", "1500 nits"))
                )
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(existing));
        when(ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot("Matrix P4 64x64 Refresh", modelId)).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedModelListResponse.LedModelItem result = useCase.execute(modelId, request);

        assertThat(result.modelName()).isEqualTo("Matrix P4 64x64 Refresh");
        assertThat(result.stockQuantity()).isEqualTo(30L);
        assertThat(result.spec().widthPx()).isEqualTo(64);
        assertThat(result.spec().heightPx()).isEqualTo(64);
        assertThat(result.spec().attributes()).hasSize(1);
        assertThat(result.spec().attributes().get(0).label()).isEqualTo("Brightness");
    }

    @Test
    @DisplayName("DEV-03 UTCID04 [A]: Non-existent modelId throws LedModelNotFoundException (404)")
    void execute_modelNotFound_throwsLedModelNotFoundException() {
        Long modelId = 999L;
        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Non-existent Model",
                10L,
                new UpdateLedModelRequest.LedSpecRequest(64, 64, List.of())
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(modelId, request))
                .isInstanceOf(LedModelNotFoundException.class)
                .hasMessageContaining("LED model with id 999 not found.");

        verifyNoInteractions(ledInstanceRepository);
    }

    @Test
    @DisplayName("DEV-03 UTCID05 [A]: Duplicate modelName on another model throws DuplicateLedModelNameException (409)")
    void execute_duplicateModelNameOnOtherModel_throwsDuplicateException() {
        Long modelId = 4L;
        LedModelEntity existing = createSampleEntity(modelId, "Lumintra Pro A", 64, 64, 10L, LedModelStatus.ACTIVE);

        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Lumintra Pro B",
                10L,
                new UpdateLedModelRequest.LedSpecRequest(64, 64, List.of())
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(existing));
        when(ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot("Lumintra Pro B", modelId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(modelId, request))
                .isInstanceOf(DuplicateLedModelNameException.class)
                .hasMessageContaining("Lumintra Pro B");

        verifyNoInteractions(ledInstanceRepository);
    }

    @Test
    @DisplayName("DEV-03 UTCID06 [B]: Updating stock to 0 automatically transitions status to OUT_OF_STOCK")
    void execute_updateStockToZero_transitionsToOutOfStock() {
        Long modelId = 5L;
        LedModelEntity existing = createSampleEntity(modelId, "Matrix P4 32x32", 32, 32, 10L, LedModelStatus.ACTIVE);

        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Matrix P4 32x32",
                0L,
                new UpdateLedModelRequest.LedSpecRequest(32, 32, List.of())
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(existing));
        when(ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot("Matrix P4 32x32", modelId)).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedModelListResponse.LedModelItem result = useCase.execute(modelId, request);

        assertThat(result.stockQuantity()).isEqualTo(0L);
        assertThat(result.status()).isEqualTo(LedModelStatus.OUT_OF_STOCK);
        assertThat(result.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("DEV-03 UTCID07 [B]: Restoring positive stock automatically transitions status to ACTIVE")
    void execute_restoringPositiveStock_transitionsToActive() {
        Long modelId = 6L;
        LedModelEntity existing = createSampleEntity(modelId, "Matrix P4 32x32", 32, 32, 0L, LedModelStatus.OUT_OF_STOCK);

        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Matrix P4 32x32",
                50L,
                new UpdateLedModelRequest.LedSpecRequest(32, 32, List.of())
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(existing));
        when(ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot("Matrix P4 32x32", modelId)).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedModelListResponse.LedModelItem result = useCase.execute(modelId, request);

        assertThat(result.stockQuantity()).isEqualTo(50L);
        assertThat(result.status()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(result.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("DEV-03 UTCID08 [N]: DEPRECATED model status remains DEPRECATED even if stock is updated")
    void execute_deprecatedModel_preservesDeprecatedStatus() {
        Long modelId = 7L;
        LedModelEntity existing = createSampleEntity(modelId, "Legacy Matrix", 32, 32, 0L, LedModelStatus.DEPRECATED);

        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Legacy Matrix (Clearance)",
                5L,
                new UpdateLedModelRequest.LedSpecRequest(32, 32, List.of())
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(existing));
        when(ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot("Legacy Matrix (Clearance)", modelId)).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedModelListResponse.LedModelItem result = useCase.execute(modelId, request);

        assertThat(result.stockQuantity()).isEqualTo(5L);
        assertThat(result.status()).isEqualTo(LedModelStatus.DEPRECATED);
    }

    @Test
    @DisplayName("DEV-03 UTCID09 [N]: Preserves exact reordering and editing of specification attributes in JSONB")
    void execute_reorderAndEditAttributes_preservesExactOrder() {
        Long modelId = 8L;
        LedModelEntity existing = createSampleEntity(modelId, "Custom Model", 64, 64, 10L, LedModelStatus.ACTIVE);

        UpdateLedModelRequest request = new UpdateLedModelRequest(
                "Custom Model",
                10L,
                new UpdateLedModelRequest.LedSpecRequest(
                        64,
                        64,
                        List.of(
                                new UpdateLedModelRequest.SpecAttributeRequest("C - Connectivity", "Wi-Fi"),
                                new UpdateLedModelRequest.SpecAttributeRequest("A - Voltage", "5V USB-C"),
                                new UpdateLedModelRequest.SpecAttributeRequest("B - Pitch", "P4")
                        )
                )
        );

        when(ledModelRepository.findById(modelId)).thenReturn(Optional.of(existing));
        when(ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot("Custom Model", modelId)).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LedModelListResponse.LedModelItem result = useCase.execute(modelId, request);

        List<LedModelListResponse.SpecAttribute> attributes = result.spec().attributes();
        assertThat(attributes).hasSize(3);
        assertThat(attributes.get(0).label()).isEqualTo("C - Connectivity");
        assertThat(attributes.get(1).label()).isEqualTo("A - Voltage");
        assertThat(attributes.get(2).label()).isEqualTo("B - Pitch");
    }
}
