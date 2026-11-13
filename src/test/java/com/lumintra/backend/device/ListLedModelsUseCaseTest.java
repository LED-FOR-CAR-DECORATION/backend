package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.LedModelFilterRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.application.ListLedModelsUseCase;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListLedModelsUseCaseTest {

    @Mock
    private LedModelRepository ledModelRepository;

    private ListLedModelsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListLedModelsUseCase(ledModelRepository, new LedModelMapper());
    }

    @Test
    @DisplayName("DEV-01 UTCID08 [N]: Executes specification query sorted by modelId DESC and maps pagination metadata")
    void execute_validFilter_queriesRepositoryAndMapsItems() {
        LedModelEntity entity = LedModelEntity.createForProduct(
                101L,
                "Lumintra Matrix 64x64 Pro",
                Map.of(
                        "widthPx", 64,
                        "heightPx", 64,
                        "attributes", List.of(Map.of("label", "Voltage", "value", "5V USB-C"))
                ),
                12L
        );

        PageRequest expectedPageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "modelId"));
        Page<LedModelEntity> repoPage = new PageImpl<>(List.of(entity), expectedPageable, 6L);

        when(ledModelRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(repoPage);

        LedModelFilterRequest filter = new LedModelFilterRequest(
                1, 5, LedModelStatus.ACTIVE, 64, 64, false, false, "Matrix");

        LedModelListResponse response = useCase.execute(filter);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ledModelRepository).findAll(any(Specification.class), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(1);
        assertThat(capturedPageable.getPageSize()).isEqualTo(5);
        assertThat(capturedPageable.getSort().getOrderFor("modelId"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);

        assertThat(response.items()).hasSize(1);
        LedModelListResponse.LedModelItem item = response.items().get(0);
        assertThat(item.modelId()).isEqualTo(101L);
        assertThat(item.productId()).isEqualTo(101L);
        assertThat(item.isLowStock()).isFalse();
        assertThat(item.spec().widthPx()).isEqualTo(64);
        assertThat(item.spec().heightPx()).isEqualTo(64);
        assertThat(item.spec().attributes())
                .containsExactly(new LedModelListResponse.SpecAttribute("Voltage", "5V USB-C"));

        assertThat(response.meta().getPage()).isEqualTo(1);
        assertThat(response.meta().getSize()).isEqualTo(5);
        assertThat(response.meta().getTotalElements()).isEqualTo(6L);
        assertThat(response.meta().getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("DEV-01 UTCID09 [B]: Handles null filter request and empty result page cleanly")
    void execute_nullFilterAndEmptyPage_defaultsPaginationAndReturnsEmptyItems() {
        PageRequest defaultPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modelId"));
        Page<LedModelEntity> emptyPage = new PageImpl<>(List.of(), defaultPageable, 0L);

        when(ledModelRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        LedModelListResponse response = useCase.execute(null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ledModelRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);

        assertThat(response.items()).isEmpty();
        assertThat(response.meta().getTotalElements()).isZero();
        assertThat(response.meta().getTotalPages()).isZero();
    }
}
