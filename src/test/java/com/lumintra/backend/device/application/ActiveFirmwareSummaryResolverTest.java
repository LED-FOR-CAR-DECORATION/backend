package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.LedModelListResponse.ActiveFirmwareSummary;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveFirmwareSummaryResolverTest {

    @Mock
    private FirmwareVersionRepository firmwareVersionRepository;

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    private ActiveFirmwareSummaryResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ActiveFirmwareSummaryResolver(firmwareVersionRepository, ledInstanceRepository);
    }

    private static FirmwareVersionEntity firmware(long id, long modelId, String version, String uploadedAt) {
        FirmwareVersionEntity entity = FirmwareVersionEntity.create(modelId, version, null,
                "firmware/" + modelId + "/" + version + ".bin", 1024L, "b".repeat(64),
                FirmwareStatus.ACTIVE, 1L, Instant.parse(uploadedAt));
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
    @DisplayName("#284 Resolver [B]: Empty or null model ids query nothing")
    void resolve_noModelIds_returnsEmptyWithoutQueries() {
        assertThat(resolver.resolve(List.of())).isEmpty();
        assertThat(resolver.resolve((List<Long>) null)).isEmpty();
        assertThat(resolver.resolve(Arrays.asList(null, null))).isEmpty();
        assertThat(resolver.resolve((Long) null)).isNull();
        verifyNoInteractions(firmwareVersionRepository, ledInstanceRepository);
    }

    @Test
    @DisplayName("#284 Resolver [N]: Models without an ACTIVE release are absent and device counts are skipped")
    void resolve_noActiveReleases_skipsCountQuery() {
        when(firmwareVersionRepository.findByModelIdInAndStatus(List.of(1L, 2L), FirmwareStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(resolver.resolve(List.of(1L, 2L, 1L))).isEmpty();
        verify(ledInstanceRepository, never()).countBoundDevicesByFirmwareIds(anyCollection());
    }

    @Test
    @DisplayName("#284 Resolver [B]: Missing device count defaults to 0 and duplicate ACTIVE rows keep the newest upload")
    void resolve_duplicatesAndMissingCounts_newestWinsAndZeroCount() {
        FirmwareVersionEntity older = firmware(20L, 1L, "1.0.0", "2026-11-01T00:00:00Z");
        FirmwareVersionEntity newer = firmware(21L, 1L, "1.1.0", "2026-11-10T00:00:00Z");
        FirmwareVersionEntity other = firmware(30L, 2L, "2.0.0", "2026-11-05T00:00:00Z");
        when(firmwareVersionRepository.findByModelIdInAndStatus(List.of(1L, 2L), FirmwareStatus.ACTIVE))
                .thenReturn(List.of(older, other, newer));
        when(ledInstanceRepository.countBoundDevicesByFirmwareIds(anyCollection()))
                .thenReturn(List.of(count(21L, 7L)));

        Map<Long, ActiveFirmwareSummary> result = resolver.resolve(List.of(1L, 2L));

        assertThat(result.get(1L).firmwareId()).isEqualTo(21L);
        assertThat(result.get(1L).version()).isEqualTo("1.1.0");
        assertThat(result.get(1L).installedDeviceCount()).isEqualTo(7L);
        assertThat(result.get(2L).firmwareId()).isEqualTo(30L);
        assertThat(result.get(2L).installedDeviceCount()).isZero();
    }

    @Test
    @DisplayName("#284 Resolver [N]: Single-model lookup returns that model's summary")
    void resolve_singleModel_returnsSummary() {
        when(firmwareVersionRepository.findByModelIdInAndStatus(List.of(5L), FirmwareStatus.ACTIVE))
                .thenReturn(List.of(firmware(40L, 5L, "3.0.0", "2026-11-20T10:00:00Z")));
        when(ledInstanceRepository.countBoundDevicesByFirmwareIds(List.of(40L))).thenReturn(List.of(count(40L, 2L)));

        ActiveFirmwareSummary summary = resolver.resolve(5L);

        assertThat(summary.version()).isEqualTo("3.0.0");
        assertThat(summary.uploadedAt()).isEqualTo(Instant.parse("2026-11-20T10:00:00Z"));
        assertThat(summary.installedDeviceCount()).isEqualTo(2L);
    }
}
