package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.DeprecateFirmwareRequest;
import com.lumintra.backend.device.api.response.DeprecateFirmwareResponse;
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
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.device.FirmwareAlreadyDeprecatedException;
import com.lumintra.backend.shared.exception.device.FirmwareNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeprecateFirmwareUseCaseTest {

    @Mock private FirmwareVersionRepository firmwareVersionRepository;
    @Mock private LedModelRepository ledModelRepository;
    @Mock private LedInstanceRepository ledInstanceRepository;
    @Mock private InternalEmployeeAccessService employeeAccess;
    @Mock private EmployeeLookupService employeeLookup;

    private DeprecateFirmwareUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeprecateFirmwareUseCase(firmwareVersionRepository, ledModelRepository, ledInstanceRepository,
                employeeAccess, employeeLookup, new FirmwareReleaseMapper());
        when(employeeAccess.requireActiveEmployeeId("7", Set.of("ADMIN"))).thenReturn(71L);
        when(ledModelRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(LedModelEntity.createCatalogModel(
                3L, null, "Matrix 64x64", 64, 64, Map.of(), 5L, LedModelStatus.ACTIVE)));
        when(employeeLookup.findProfilesByEmployeeIds(anyCollection())).thenReturn(Map.of(
                70L, new EmployeeProfileSnapshot(70L, "Nguyen Van A", "a@lumintra.shop"),
                71L, new EmployeeProfileSnapshot(71L, "Tran Thi B", "b@lumintra.shop")));
        when(ledInstanceRepository.countBoundDevicesByFirmwareIds(anyCollection())).thenReturn(List.of(count(12L, 5L)));
    }

    private static FirmwareVersionEntity release(long id, String version, FirmwareStatus status, String uploadedAt) {
        FirmwareVersionEntity entity = FirmwareVersionEntity.create(3L, version, "notes", "firmware/3/" + version + ".bin",
                2048L, "a".repeat(64), status, 70L, Instant.parse(uploadedAt));
        entity.setFirmwareId(id);
        return entity;
    }

    private static LedInstanceRepository.FirmwareInstallCount count(long firmwareId, long devices) {
        return new LedInstanceRepository.FirmwareInstallCount() {
            public Long getFirmwareId() { return firmwareId; }
            public Long getDeviceCount() { return devices; }
        };
    }

    private void givenReleases(FirmwareVersionEntity... releases) {
        for (FirmwareVersionEntity release : releases) {
            when(firmwareVersionRepository.findModelIdByFirmwareId(release.getFirmwareId())).thenReturn(Optional.of(3L));
        }
        when(firmwareVersionRepository.findByModelId(3L)).thenReturn(List.of(releases));
    }

    @Test
    @DisplayName("DEV-13 UTCID01 [N]: Deprecating the ACTIVE release promotes the highest-version SUPERSEDED release")
    void execute_activeRelease_promotesHighestSupersededVersion() {
        FirmwareVersionEntity active = release(12L, "1.10.0", FirmwareStatus.ACTIVE, "2026-11-20T00:00:00Z");
        FirmwareVersionEntity v19 = release(11L, "1.9.0", FirmwareStatus.SUPERSEDED, "2026-11-01T00:00:00Z");
        FirmwareVersionEntity v12Late = release(10L, "1.2.0", FirmwareStatus.SUPERSEDED, "2026-11-15T00:00:00Z");
        FirmwareVersionEntity deprecatedOld = release(9L, "1.9.5", FirmwareStatus.DEPRECATED, "2026-11-10T00:00:00Z");
        givenReleases(active, v19, v12Late, deprecatedOld);

        DeprecateFirmwareResponse response = useCase.execute("7", 12L, new DeprecateFirmwareRequest("  Boot loop  "));

        assertThat(active.getStatus()).isEqualTo(FirmwareStatus.DEPRECATED);
        assertThat(active.getDeprecatedByEmployeeId()).isEqualTo(71L);
        assertThat(active.getDeprecationReason()).isEqualTo("Boot loop");
        assertThat(active.getDeprecatedAt()).isNotNull();
        assertThat(v19.getStatus()).isEqualTo(FirmwareStatus.ACTIVE);
        assertThat(v12Late.getStatus()).isEqualTo(FirmwareStatus.SUPERSEDED);
        assertThat(deprecatedOld.getStatus()).isEqualTo(FirmwareStatus.DEPRECATED);

        assertThat(response.deprecated().firmwareId()).isEqualTo(12L);
        assertThat(response.deprecated().status()).isEqualTo(FirmwareStatus.DEPRECATED);
        assertThat(response.deprecated().deprecatedBy()).isEqualTo("Tran Thi B");
        assertThat(response.deprecated().uploadedBy()).isEqualTo("Nguyen Van A");
        assertThat(response.deprecated().modelName()).isEqualTo("Matrix 64x64");
        assertThat(response.deprecated().installedDeviceCount()).isEqualTo(5L);
        assertThat(response.newActiveFirmware().firmwareId()).isEqualTo(11L);
        assertThat(response.newActiveFirmware().status()).isEqualTo(FirmwareStatus.ACTIVE);
        assertThat(response.affectedDeviceCount()).isEqualTo(5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FirmwareVersionEntity>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(firmwareVersionRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).containsExactly(active, v19);
    }

    @Test
    @DisplayName("DEV-13 UTCID02 [N]: Deprecating a SUPERSEDED release promotes nothing and keeps the ACTIVE release")
    void execute_supersededRelease_promotesNothing() {
        FirmwareVersionEntity active = release(12L, "1.2.0", FirmwareStatus.ACTIVE, "2026-11-20T00:00:00Z");
        FirmwareVersionEntity superseded = release(11L, "1.1.0", FirmwareStatus.SUPERSEDED, "2026-11-01T00:00:00Z");
        givenReleases(active, superseded);
        when(ledInstanceRepository.countBoundDevicesByFirmwareIds(List.of(11L))).thenReturn(List.of());

        DeprecateFirmwareResponse response = useCase.execute("7", 11L, new DeprecateFirmwareRequest("Old bug"));

        assertThat(superseded.getStatus()).isEqualTo(FirmwareStatus.DEPRECATED);
        assertThat(active.getStatus()).isEqualTo(FirmwareStatus.ACTIVE);
        assertThat(response.newActiveFirmware()).isNull();
        assertThat(response.affectedDeviceCount()).isZero();
    }

    @Test
    @DisplayName("DEV-13 UTCID03 [B]: Deprecating the only release leaves the model with no ACTIVE release")
    void execute_onlyRelease_leavesNoActive() {
        FirmwareVersionEntity only = release(12L, "1.0.0", FirmwareStatus.ACTIVE, "2026-11-20T00:00:00Z");
        givenReleases(only);

        DeprecateFirmwareResponse response = useCase.execute("7", 12L, new DeprecateFirmwareRequest("Recall"));

        assertThat(only.getStatus()).isEqualTo(FirmwareStatus.DEPRECATED);
        assertThat(response.newActiveFirmware()).isNull();
        assertThat(response.affectedDeviceCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("DEV-13 UTCID04 [A]: Unknown release returns FIRMWARE_NOT_FOUND without changes")
    void execute_unknownRelease_throwsNotFound() {
        when(firmwareVersionRepository.findModelIdByFirmwareId(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("7", 404L, new DeprecateFirmwareRequest("x")))
                .isInstanceOf(FirmwareNotFoundException.class);
        verify(firmwareVersionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("DEV-13 UTCID05 [A]: Already DEPRECATED release returns FIRMWARE_ALREADY_DEPRECATED and cannot be undone")
    void execute_alreadyDeprecated_throwsConflict() {
        FirmwareVersionEntity deprecated = release(9L, "1.0.0", FirmwareStatus.DEPRECATED, "2026-11-01T00:00:00Z");
        deprecated.deprecate(70L, "first", Instant.parse("2026-11-02T00:00:00Z"));
        givenReleases(deprecated);

        assertThatThrownBy(() -> useCase.execute("7", 9L, new DeprecateFirmwareRequest("again")))
                .isInstanceOf(FirmwareAlreadyDeprecatedException.class);
        assertThat(deprecated.getDeprecationReason()).isEqualTo("first");
        verify(firmwareVersionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    @DisplayName("DEV-13 UTCID06 [A]: Caller that is not an active ADMIN is rejected before any lookup")
    void execute_notAdmin_isRejected() {
        when(employeeAccess.requireActiveEmployeeId("8", Set.of("ADMIN")))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> useCase.execute("8", 12L, new DeprecateFirmwareRequest("x")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(firmwareVersionRepository, ledModelRepository);
    }
}
