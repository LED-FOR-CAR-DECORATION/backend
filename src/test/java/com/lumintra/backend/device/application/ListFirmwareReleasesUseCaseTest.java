package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.FirmwareFilterRequest;
import com.lumintra.backend.device.api.response.FirmwareReleaseResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.FirmwareReleaseMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListFirmwareReleasesUseCaseTest {

    @Mock private FirmwareVersionRepository firmwareVersionRepository;
    @Mock private LedModelRepository ledModelRepository;
    @Mock private LedInstanceRepository ledInstanceRepository;
    @Mock private EmployeeLookupService employeeLookup;

    private ListFirmwareReleasesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListFirmwareReleasesUseCase(firmwareVersionRepository, ledModelRepository,
                ledInstanceRepository, employeeLookup, new FirmwareReleaseMapper());
    }

    private static FirmwareVersionEntity release(long id, long modelId, String version, FirmwareStatus status, Long uploader) {
        FirmwareVersionEntity entity = FirmwareVersionEntity.create(modelId, version, "notes " + version,
                "firmware/" + modelId + "/" + version + ".bin", 2048L, "d".repeat(64), status, uploader,
                Instant.parse("2026-11-20T10:00:00Z"));
        entity.setFirmwareId(id);
        return entity;
    }

    private static LedInstanceRepository.FirmwareInstallCount count(long firmwareId, long devices) {
        return new LedInstanceRepository.FirmwareInstallCount() {
            public Long getFirmwareId() { return firmwareId; }
            public Long getDeviceCount() { return devices; }
        };
    }

    @Test
    @DisplayName("DEV-11 UTCID01 [N]: Maps every release field with model name, admin names and installed counts from batch lookups")
    void execute_mapsReleasesWithBatchLookups() {
        FirmwareVersionEntity active = release(12L, 3L, "1.2.0", FirmwareStatus.ACTIVE, 70L);
        FirmwareVersionEntity deprecated = release(11L, 3L, "1.1.0", FirmwareStatus.DEPRECATED, 70L);
        deprecated.setDeprecatedAt(Instant.parse("2026-11-19T00:00:00Z"));
        deprecated.setDeprecatedByEmployeeId(71L);
        deprecated.setDeprecationReason("Boot loop on 64x64");
        PageRequest pageable = PageRequest.of(0, 20);
        when(firmwareVersionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(active, deprecated), pageable, 2));
        when(ledModelRepository.findAllById(Set.of(3L))).thenReturn(List.of(LedModelEntity.createCatalogModel(
                3L, null, "Matrix 64x64", 64, 64, Map.of(), 5L, LedModelStatus.ACTIVE)));
        when(employeeLookup.findProfilesByEmployeeIds(Set.of(70L, 71L))).thenReturn(Map.of(
                70L, new EmployeeProfileSnapshot(70L, "Nguyen Van A", "a@lumintra.shop"),
                71L, new EmployeeProfileSnapshot(71L, "Tran Thi B", "b@lumintra.shop")));
        when(ledInstanceRepository.countBoundDevicesByFirmwareIds(List.of(12L, 11L))).thenReturn(List.of(count(11L, 4L)));

        ListFirmwareReleasesUseCase.Result result = useCase.execute(new FirmwareFilterRequest(0, 20, 3L, null));

        FirmwareReleaseResponse first = result.items().get(0);
        assertThat(first.firmwareId()).isEqualTo(12L);
        assertThat(first.modelName()).isEqualTo("Matrix 64x64");
        assertThat(first.version()).isEqualTo("1.2.0");
        assertThat(first.status()).isEqualTo(FirmwareStatus.ACTIVE);
        assertThat(first.changelog()).isEqualTo("notes 1.2.0");
        assertThat(first.fileSizeBytes()).isEqualTo(2048L);
        assertThat(first.sha256()).hasSize(64);
        assertThat(first.uploadedBy()).isEqualTo("Nguyen Van A");
        assertThat(first.uploadedAt()).isEqualTo(Instant.parse("2026-11-20T10:00:00Z"));
        assertThat(first.deprecatedAt()).isNull();
        assertThat(first.deprecatedBy()).isNull();
        assertThat(first.installedDeviceCount()).isZero();

        FirmwareReleaseResponse second = result.items().get(1);
        assertThat(second.deprecatedAt()).isEqualTo(Instant.parse("2026-11-19T00:00:00Z"));
        assertThat(second.deprecatedBy()).isEqualTo("Tran Thi B");
        assertThat(second.deprecationReason()).isEqualTo("Boot loop on 64x64");
        assertThat(second.installedDeviceCount()).isEqualTo(4L);

        assertThat(result.meta().getTotalElements()).isEqualTo(2);
        assertThat(result.meta().getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("DEV-11 UTCID02 [N]: Requests newest upload first with the given page and size")
    void execute_sortsByUploadedAtDescending() {
        when(firmwareVersionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 1), 0));

        useCase.execute(new FirmwareFilterRequest(2, 1, null, FirmwareStatus.ACTIVE));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(firmwareVersionRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
        assertThat(captor.getValue().getSort().getOrderFor("uploadedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("DEV-11 UTCID03 [B]: Null filter defaults to page 0 size 20 and an empty page skips all lookups")
    void execute_nullFilterEmptyPage_skipsLookups() {
        when(firmwareVersionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ListFirmwareReleasesUseCase.Result result = useCase.execute(null);

        assertThat(result.items()).isEmpty();
        assertThat(result.meta().getSize()).isEqualTo(20);
        verifyNoInteractions(ledModelRepository, employeeLookup);
        verify(ledInstanceRepository, never()).countBoundDevicesByFirmwareIds(anyCollection());
    }

    @Test
    @DisplayName("DEV-11 UTCID04 [B]: Releases without a known uploader or model keep those fields null")
    void execute_unknownUploaderAndModel_returnsNulls() {
        when(firmwareVersionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(release(5L, 9L, "0.1.0", FirmwareStatus.SUPERSEDED, null))));
        when(ledModelRepository.findAllById(Set.of(9L))).thenReturn(List.of());
        when(ledInstanceRepository.countBoundDevicesByFirmwareIds(List.of(5L))).thenReturn(List.of());

        FirmwareReleaseResponse item = useCase.execute(new FirmwareFilterRequest(0, 20, null, null)).items().getFirst();

        assertThat(item.modelName()).isNull();
        assertThat(item.uploadedBy()).isNull();
        verifyNoInteractions(employeeLookup);
    }
}
