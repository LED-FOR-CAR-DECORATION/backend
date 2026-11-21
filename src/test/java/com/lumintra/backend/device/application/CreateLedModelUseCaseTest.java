package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.CreateLedModelRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.DuplicateLedModelNameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateLedModelUseCaseTest {

    @Mock
    private LedModelRepository ledModelRepository;

    private LedModelMapper ledModelMapper;
    private CreateLedModelUseCase useCase;

    @BeforeEach
    void setUp() {
        ledModelMapper = new LedModelMapper();
        useCase = new CreateLedModelUseCase(ledModelRepository, ledModelMapper);
    }

    @Test
    @DisplayName("DEV-02 UTCID01 [N]: Create standalone LED model with positive stock succeeds and sets status ACTIVE")
    void execute_validRequestWithPositiveStock_createsActiveModel() {
        CreateLedModelRequest request = new CreateLedModelRequest(
                "Lumintra Matrix 64x64 Pro V2",
                25L,
                new CreateLedModelRequest.LedSpecRequest(
                        64,
                        64,
                        List.of(
                                new CreateLedModelRequest.SpecAttributeRequest("Điện áp", "5V USB-C"),
                                new CreateLedModelRequest.SpecAttributeRequest("Khoảng cách điểm ảnh", "P4 (4mm)")
                        )
                )
        );

        when(ledModelRepository.existsByModelNameIgnoreCase("Lumintra Matrix 64x64 Pro V2")).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> {
            LedModelEntity entity = invocation.getArgument(0);
            entity.setModelId(101L);
            return entity;
        });

        LedModelListResponse.LedModelItem result = useCase.execute(request);

        assertThat(result).isNotNull();
        assertThat(result.modelId()).isEqualTo(101L);
        assertThat(result.productId()).isNull();
        assertThat(result.modelName()).isEqualTo("Lumintra Matrix 64x64 Pro V2");
        assertThat(result.stockQuantity()).isEqualTo(25L);
        assertThat(result.status()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(result.isLowStock()).isFalse();

        assertThat(result.spec()).isNotNull();
        assertThat(result.spec().widthPx()).isEqualTo(64);
        assertThat(result.spec().heightPx()).isEqualTo(64);
        assertThat(result.spec().attributes()).hasSize(2);
        assertThat(result.spec().attributes().get(0).label()).isEqualTo("Điện áp");
        assertThat(result.spec().attributes().get(0).value()).isEqualTo("5V USB-C");
        assertThat(result.spec().attributes().get(1).label()).isEqualTo("Khoảng cách điểm ảnh");
        assertThat(result.spec().attributes().get(1).value()).isEqualTo("P4 (4mm)");

        ArgumentCaptor<LedModelEntity> captor = ArgumentCaptor.forClass(LedModelEntity.class);
        verify(ledModelRepository).save(captor.capture());
        LedModelEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getModelName()).isEqualTo("Lumintra Matrix 64x64 Pro V2");
        assertThat(savedEntity.getWidthPx()).isEqualTo(64);
        assertThat(savedEntity.getHeightPx()).isEqualTo(64);
        assertThat(savedEntity.getStockQuantity()).isEqualTo(25L);
        assertThat(savedEntity.getStatus()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(savedEntity.getProductId()).isNull();

        Map<String, Object> spec = savedEntity.getSpec();
        assertThat(spec).containsEntry("widthPx", 64).containsEntry("heightPx", 64);
        assertThat(spec.get("attributes")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("DEV-02 UTCID02 [N]: Create standalone LED model with zero stock sets status OUT_OF_STOCK")
    void execute_validRequestWithZeroStock_createsOutOfStockModel() {
        CreateLedModelRequest request = new CreateLedModelRequest(
                "Lumintra Matrix 32x32 Mini",
                0L,
                new CreateLedModelRequest.LedSpecRequest(
                        32,
                        32,
                        List.of(new CreateLedModelRequest.SpecAttributeRequest("Voltage", "5V"))
                )
        );

        when(ledModelRepository.existsByModelNameIgnoreCase("Lumintra Matrix 32x32 Mini")).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> {
            LedModelEntity entity = invocation.getArgument(0);
            entity.setModelId(102L);
            return entity;
        });

        LedModelListResponse.LedModelItem result = useCase.execute(request);

        assertThat(result).isNotNull();
        assertThat(result.modelId()).isEqualTo(102L);
        assertThat(result.stockQuantity()).isEqualTo(0L);
        assertThat(result.status()).isEqualTo(LedModelStatus.OUT_OF_STOCK);
        assertThat(result.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("DEV-02 UTCID03 [A]: Duplicate modelName throws DuplicateLedModelNameException (409)")
    void execute_duplicateModelName_throwsDuplicateException() {
        CreateLedModelRequest request = new CreateLedModelRequest(
                "Lumintra Matrix 64x64 Pro",
                10L,
                new CreateLedModelRequest.LedSpecRequest(
                        64,
                        64,
                        List.of()
                )
        );

        when(ledModelRepository.existsByModelNameIgnoreCase("Lumintra Matrix 64x64 Pro")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(DuplicateLedModelNameException.class)
                .hasMessageContaining("Lumintra Matrix 64x64 Pro");

        verify(ledModelRepository).existsByModelNameIgnoreCase("Lumintra Matrix 64x64 Pro");
    }

    @Test
    @DisplayName("DEV-02 UTCID04 [B]: Model name with leading/trailing whitespace is trimmed before check and save")
    void execute_nameWithWhitespace_trimsName() {
        CreateLedModelRequest request = new CreateLedModelRequest(
                "   Lumintra Matrix 128x64 Ultra   ",
                5L,
                new CreateLedModelRequest.LedSpecRequest(
                        128,
                        64,
                        List.of()
                )
        );

        when(ledModelRepository.existsByModelNameIgnoreCase("Lumintra Matrix 128x64 Ultra")).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> {
            LedModelEntity entity = invocation.getArgument(0);
            entity.setModelId(103L);
            return entity;
        });

        LedModelListResponse.LedModelItem result = useCase.execute(request);

        assertThat(result.modelName()).isEqualTo("Lumintra Matrix 128x64 Ultra");
        verify(ledModelRepository).existsByModelNameIgnoreCase("Lumintra Matrix 128x64 Ultra");
    }

    @Test
    @DisplayName("DEV-02 UTCID05 [N]: Preserves exact display order of specification attributes in JSONB spec")
    void execute_preservesOrderedAttributes() {
        CreateLedModelRequest request = new CreateLedModelRequest(
                "Lumintra Custom Spec Model",
                15L,
                new CreateLedModelRequest.LedSpecRequest(
                        16,
                        16,
                        List.of(
                                new CreateLedModelRequest.SpecAttributeRequest("Attr 1", "Val 1"),
                                new CreateLedModelRequest.SpecAttributeRequest("Attr 2", "Val 2"),
                                new CreateLedModelRequest.SpecAttributeRequest("Attr 3", "Val 3"),
                                new CreateLedModelRequest.SpecAttributeRequest("Attr 4", "Val 4")
                        )
                )
        );

        when(ledModelRepository.existsByModelNameIgnoreCase("Lumintra Custom Spec Model")).thenReturn(false);
        when(ledModelRepository.save(any(LedModelEntity.class))).thenAnswer(invocation -> {
            LedModelEntity entity = invocation.getArgument(0);
            entity.setModelId(104L);
            return entity;
        });

        LedModelListResponse.LedModelItem result = useCase.execute(request);

        List<LedModelListResponse.SpecAttribute> attributes = result.spec().attributes();
        assertThat(attributes).hasSize(4);
        assertThat(attributes.get(0).label()).isEqualTo("Attr 1");
        assertThat(attributes.get(1).label()).isEqualTo("Attr 2");
        assertThat(attributes.get(2).label()).isEqualTo("Attr 3");
        assertThat(attributes.get(3).label()).isEqualTo("Attr 4");
    }
}
