package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.LedModelFilterRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.application.ActiveFirmwareSummaryResolver;
import com.lumintra.backend.device.application.ListLedModelsUseCase;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListLedModelsUseCaseTest {

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private FirmwareVersionRepository firmwareVersionRepository;

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    private ListLedModelsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListLedModelsUseCase(ledModelRepository, new LedModelMapper(),
                new ActiveFirmwareSummaryResolver(firmwareVersionRepository, ledInstanceRepository));
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
        verify(firmwareVersionRepository, never()).findByModelIdInAndStatus(anyCollection(), any());
        assertThat(response.meta().getTotalElements()).isZero();
        assertThat(response.meta().getTotalPages()).isZero();
    }

    @Test
    @DisplayName("#284 UTCID01 [N]: Attaches activeFirmware per model using one batch query each for releases and device counts")
    void execute_pageWithAndWithoutActiveFirmware_batchesFirmwareLookups() {
        LedModelEntity withFirmware = LedModelEntity.createCatalogModel(
                10L, null, "Matrix A", 64, 64, Map.of("widthPx", 64, "heightPx", 64), 20L, LedModelStatus.ACTIVE);
        LedModelEntity withoutFirmware = LedModelEntity.createCatalogModel(
                9L, null, "Matrix B", 32, 64, Map.of("widthPx", 32, "heightPx", 64), 3L, LedModelStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "modelId"));
        when(ledModelRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withFirmware, withoutFirmware), pageable, 2L));

        FirmwareVersionEntity active = FirmwareVersionEntity.create(10L, "1.2.0", "Fixes", "firmware/10/1.2.0.bin",
                1_048_576L, "a".repeat(64), FirmwareStatus.ACTIVE, 1L, Instant.parse("2026-11-20T10:00:00Z"));
        active.setFirmwareId(12L);
        when(firmwareVersionRepository.findByModelIdInAndStatus(List.of(10L, 9L), FirmwareStatus.ACTIVE))
                .thenReturn(List.of(active));
        LedInstanceRepository.FirmwareInstallCount count = new LedInstanceRepository.FirmwareInstallCount() {
            public Long getFirmwareId() { return 12L; }
            public Long getDeviceCount() { return 5L; }
        };
        when(ledInstanceRepository.countBoundDevicesByFirmwareIds(List.of(12L))).thenReturn(List.of(count));

        LedModelListResponse response = useCase.execute(null);

        LedModelListResponse.ActiveFirmwareSummary summary = response.items().get(0).activeFirmware();
        assertThat(summary.firmwareId()).isEqualTo(12L);
        assertThat(summary.version()).isEqualTo("1.2.0");
        assertThat(summary.uploadedAt()).isEqualTo(Instant.parse("2026-11-20T10:00:00Z"));
        assertThat(summary.installedDeviceCount()).isEqualTo(5L);
        assertThat(response.items().get(1).activeFirmware()).isNull();
        verify(firmwareVersionRepository, times(1)).findByModelIdInAndStatus(anyCollection(), any());
        verify(ledInstanceRepository, times(1)).countBoundDevicesByFirmwareIds(anyCollection());
    }
}
