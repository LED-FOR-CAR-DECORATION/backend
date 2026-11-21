package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.UploadFirmwareRequest;
import com.lumintra.backend.device.api.response.UploadFirmwareResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.service.FirmwareBinaryStorage;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.FirmwareReleaseMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.device.FirmwareVersionConflictException;
import com.lumintra.backend.shared.exception.device.InvalidFirmwareBinaryException;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadFirmwareUseCaseTest {

    private static final long MAX_SIZE = 4096;

    @Mock private LedModelRepository ledModelRepository;
    @Mock private FirmwareVersionRepository firmwareVersionRepository;
    @Mock private FirmwareBinaryStorage binaryStorage;
    @Mock private InternalEmployeeAccessService employeeAccess;
    @Mock private EmployeeLookupService employeeLookup;

    private UploadFirmwareUseCase useCase;
    private LedModelEntity model;

    @BeforeEach
    void setUp() {
        useCase = new UploadFirmwareUseCase(ledModelRepository, firmwareVersionRepository, binaryStorage,
                new FirmwareReleaseMapper(), employeeAccess, employeeLookup, MAX_SIZE);
        model = LedModelEntity.createCatalogModel(3L, null, "Matrix 64x64", 64, 64,
                Map.of("widthPx", 64, "heightPx", 64), 10L, LedModelStatus.ACTIVE);
        when(employeeAccess.requireActiveEmployeeId("7", Set.of("ADMIN"))).thenReturn(70L);
        when(employeeLookup.findProfileByEmployeeId(70L))
                .thenReturn(Optional.of(new EmployeeProfileSnapshot(70L, "Nguyen Van A", "admin@lumintra.shop")));
        when(ledModelRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(model));
        when(firmwareVersionRepository.findByModelId(3L)).thenReturn(List.of());
        when(firmwareVersionRepository.saveAndFlush(any(FirmwareVersionEntity.class))).thenAnswer(inv -> {
            FirmwareVersionEntity entity = inv.getArgument(0);
            entity.setFirmwareId(99L);
            return entity;
        });
    }

    private static byte[] image(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xE9;
        for (int i = 1; i < size; i++) bytes[i] = (byte) i;
        return bytes;
    }

    private static UploadFirmwareRequest request(byte[] content, String version) {
        return new UploadFirmwareRequest(
                new MockMultipartFile("file", "app.bin", "application/octet-stream", content), 3L, version, "  Fix BLE  ");
    }

    private static FirmwareVersionEntity release(long id, String version, FirmwareStatus status, String uploadedAt) {
        FirmwareVersionEntity entity = FirmwareVersionEntity.create(3L, version, "old", "firmware/3/" + version + ".bin",
                1000L, "f".repeat(64), status, 70L, Instant.parse(uploadedAt));
        entity.setFirmwareId(id);
        return entity;
    }

    @Test
    @DisplayName("DEV-12 UTCID01 [N]: First release is hashed, stored privately and saved as ACTIVE")
    void execute_firstRelease_storesAndActivates() {
        byte[] binary = image(1024);

        UploadFirmwareResponse response = useCase.execute("7", request(binary, "1.0.0"));

        verify(binaryStorage).upload("firmware/3/1.0.0.bin", binary);
        ArgumentCaptor<FirmwareVersionEntity> captor = ArgumentCaptor.forClass(FirmwareVersionEntity.class);
        verify(firmwareVersionRepository).saveAndFlush(captor.capture());
        FirmwareVersionEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(FirmwareStatus.ACTIVE);
        assertThat(saved.getStorageObjectKey()).isEqualTo("firmware/3/1.0.0.bin");
        assertThat(saved.getUploadedByEmployeeId()).isEqualTo(70L);
        assertThat(saved.getChangelog()).isEqualTo("Fix BLE");
        assertThat(saved.getSha256()).isEqualTo(UploadFirmwareUseCase.sha256Hex(binary)).hasSize(64);

        assertThat(response.firmwareId()).isEqualTo(99L);
        assertThat(response.modelName()).isEqualTo("Matrix 64x64");
        assertThat(response.version()).isEqualTo("1.0.0");
        assertThat(response.fileSizeBytes()).isEqualTo(1024L);
        assertThat(response.uploadedBy()).isEqualTo("Nguyen Van A");
        assertThat(response.installedDeviceCount()).isZero();
        assertThat(response.supersededFirmwareId()).isNull();
        verify(binaryStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("DEV-12 UTCID02 [N]: Previous ACTIVE release becomes SUPERSEDED and its id is returned")
    void execute_existingActive_supersedesIt() {
        FirmwareVersionEntity active = release(11L, "1.1.0", FirmwareStatus.ACTIVE, "2026-11-10T00:00:00Z");
        FirmwareVersionEntity old = release(10L, "1.0.0", FirmwareStatus.SUPERSEDED, "2026-11-01T00:00:00Z");
        when(firmwareVersionRepository.findByModelId(3L)).thenReturn(List.of(old, active));

        UploadFirmwareResponse response = useCase.execute("7", request(image(512), "1.2.0"));

        assertThat(active.getStatus()).isEqualTo(FirmwareStatus.SUPERSEDED);
        assertThat(old.getStatus()).isEqualTo(FirmwareStatus.SUPERSEDED);
        assertThat(response.supersededFirmwareId()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo(FirmwareStatus.ACTIVE);
    }

    @Test
    @DisplayName("DEV-12 UTCID03 [N]: DEPRECATED LED models accept firmware uploads (BR-FL5-12)")
    void execute_deprecatedModel_isAllowed() {
        model.deprecate();

        UploadFirmwareResponse response = useCase.execute("7", request(image(512), "2.0.0"));

        assertThat(response.status()).isEqualTo(FirmwareStatus.ACTIVE);
    }

    @Test
    @DisplayName("DEV-12 UTCID04 [A]: Unknown model returns LED_MODEL_NOT_FOUND before touching storage")
    void execute_unknownModel_throwsNotFound() {
        when(ledModelRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("7", request(image(512), "1.0.0")))
                .isInstanceOf(LedModelNotFoundException.class);
        verifyNoInteractions(binaryStorage);
    }

    @Test
    @DisplayName("DEV-12 UTCID05 [A]: Equal, lower or duplicate versions (including deprecated) return FIRMWARE_VERSION_CONFLICT")
    void execute_versionNotIncreasing_throwsConflict() {
        when(firmwareVersionRepository.findByModelId(3L)).thenReturn(List.of(
                release(11L, "1.10.0", FirmwareStatus.DEPRECATED, "2026-11-10T00:00:00Z"),
                release(10L, "1.2.0", FirmwareStatus.ACTIVE, "2026-11-01T00:00:00Z")));

        for (String version : new String[]{"1.10.0", "1.9.9", "1.2.0", "0.9.0"}) {
            assertThatThrownBy(() -> useCase.execute("7", request(image(512), version)))
                    .isInstanceOf(FirmwareVersionConflictException.class);
        }
        verifyNoInteractions(binaryStorage);

        assertThat(useCase.execute("7", request(image(512), "1.10.1")).supersededFirmwareId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("DEV-12 UTCID06 [A]: Empty, too small, oversized or non-0xE9 files return INVALID_FIRMWARE_BINARY")
    void execute_invalidBinary_throwsInvalidBinary() {
        byte[] wrongMagic = image(512);
        wrongMagic[0] = 0x7F;

        assertThatThrownBy(() -> useCase.execute("7", request(new byte[0], "1.0.0")))
                .isInstanceOf(InvalidFirmwareBinaryException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> useCase.execute("7", request(image(287), "1.0.0")))
                .isInstanceOf(InvalidFirmwareBinaryException.class).hasMessageContaining("too small");
        assertThatThrownBy(() -> useCase.execute("7", request(image((int) MAX_SIZE + 1), "1.0.0")))
                .isInstanceOf(InvalidFirmwareBinaryException.class).hasMessageContaining("maximum size");
        assertThatThrownBy(() -> useCase.execute("7", request(wrongMagic, "1.0.0")))
                .isInstanceOf(InvalidFirmwareBinaryException.class).hasMessageContaining("0xE9");
        verifyNoInteractions(binaryStorage, ledModelRepository);
    }

    @Test
    @DisplayName("DEV-12 UTCID07 [B]: Exactly the minimum and maximum sizes are accepted")
    void execute_boundarySizes_areAccepted() {
        assertThat(useCase.execute("7", request(image(288), "1.0.0")).fileSizeBytes()).isEqualTo(288L);
        assertThat(useCase.execute("7", request(image((int) MAX_SIZE), "1.0.1")).fileSizeBytes()).isEqualTo(MAX_SIZE);
    }

    @Test
    @DisplayName("DEV-12 UTCID08 [A]: Database failure after upload deletes the stored binary (best effort)")
    void execute_saveFails_deletesUploadedBinary() {
        when(firmwareVersionRepository.saveAndFlush(any(FirmwareVersionEntity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_firmware_version_model_version"));

        assertThatThrownBy(() -> useCase.execute("7", request(image(512), "1.0.0")))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(binaryStorage).upload(any(), any());
        verify(binaryStorage).delete("firmware/3/1.0.0.bin");
    }

    @Test
    @DisplayName("DEV-12 UTCID09 [A]: Caller that is not an active ADMIN is rejected before any work")
    void execute_notAdmin_isRejected() {
        when(employeeAccess.requireActiveEmployeeId("8", Set.of("ADMIN")))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> useCase.execute("8", request(image(512), "1.0.0")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(binaryStorage, ledModelRepository, firmwareVersionRepository);
    }
}
