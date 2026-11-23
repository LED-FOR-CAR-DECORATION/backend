package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.UploadFirmwareRequest;
import com.lumintra.backend.device.api.response.FirmwareReleaseResponse;
import com.lumintra.backend.device.api.response.UploadFirmwareResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.FirmwareVersionNumber;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * DEV-12: an ADMIN uploads a firmware binary for an LED model. The binary is validated and hashed
 * on the server, stored privately, and becomes the model's single ACTIVE release.
 */
@Service
public class UploadFirmwareUseCase {

    private static final Logger log = LoggerFactory.getLogger(UploadFirmwareUseCase.class);
    private static final Set<String> ADMIN_ROLE = Set.of("ADMIN");
    static final byte ESP_IMAGE_MAGIC = (byte) 0xE9;
    /** The device firmware rejects images smaller than this (OtaManager: {@code size < 288}). */
    static final int MIN_IMAGE_BYTES = 288;

    private final LedModelRepository ledModelRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final FirmwareBinaryStorage binaryStorage;
    private final FirmwareReleaseMapper mapper;
    private final InternalEmployeeAccessService employeeAccess;
    private final EmployeeLookupService employeeLookup;
    private final long maxSizeBytes;

    public UploadFirmwareUseCase(
            LedModelRepository ledModelRepository,
            FirmwareVersionRepository firmwareVersionRepository,
            FirmwareBinaryStorage binaryStorage,
            FirmwareReleaseMapper mapper,
            InternalEmployeeAccessService employeeAccess,
            EmployeeLookupService employeeLookup,
            @Value("${app.firmware.max-size-bytes:1966080}") long maxSizeBytes) {
        this.ledModelRepository = ledModelRepository;
        this.firmwareVersionRepository = firmwareVersionRepository;
        this.binaryStorage = binaryStorage;
        this.mapper = mapper;
        this.employeeAccess = employeeAccess;
        this.employeeLookup = employeeLookup;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Transactional
    public UploadFirmwareResponse execute(String subject, UploadFirmwareRequest request) {
        Long adminId = employeeAccess.requireActiveEmployeeId(subject, ADMIN_ROLE);
        FirmwareVersionNumber version = FirmwareVersionNumber.parse(request.version());
        String changelog = request.changelog().strip();
        byte[] binary = readAndValidate(request.file());
        String sha256 = sha256Hex(binary);

        LedModelEntity model = ledModelRepository.findByIdForUpdate(request.modelId())
                .orElseThrow(() -> new LedModelNotFoundException(request.modelId()));

        List<FirmwareVersionEntity> existing = firmwareVersionRepository.findByModelId(model.getModelId());
        ensureVersionIncreases(model.getModelId(), version, existing);

        String objectKey = FirmwareBinaryStorage.objectKey(model.getModelId(), version.toString());
        binaryStorage.upload(objectKey, binary);
        deleteOnRollback(objectKey);

        FirmwareVersionEntity saved;
        Long supersededId;
        try {
            List<FirmwareVersionEntity> previouslyActive = existing.stream()
                    .filter(release -> release.getStatus() == FirmwareStatus.ACTIVE)
                    .sorted(Comparator.comparing(FirmwareVersionEntity::getUploadedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                    .toList();
            previouslyActive.forEach(FirmwareVersionEntity::supersede);
            supersededId = previouslyActive.isEmpty() ? null : previouslyActive.getFirst().getFirmwareId();

            saved = firmwareVersionRepository.saveAndFlush(FirmwareVersionEntity.create(
                    model.getModelId(), version.toString(), changelog, objectKey, binary.length, sha256,
                    FirmwareStatus.ACTIVE, adminId, Instant.now()));
        } catch (RuntimeException ex) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                binaryStorage.delete(objectKey);
            }
            throw ex;
        }

        String uploadedBy = employeeLookup.findProfileByEmployeeId(adminId)
                .map(EmployeeProfileSnapshot::fullName)
                .orElse(null);
        FirmwareReleaseResponse release = mapper.toResponse(saved, model.getModelName(), uploadedBy, null, 0L);

        log.info("Firmware uploaded [firmwareId={}] [modelId={}] [version={}] [sizeBytes={}] [supersededFirmwareId={}] [adminId={}]",
                saved.getFirmwareId(), model.getModelId(), saved.getVersion(), binary.length, supersededId, adminId);
        return UploadFirmwareResponse.of(release, supersededId);
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFirmwareBinaryException("Firmware file is empty.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new InvalidFirmwareBinaryException(
                    "Firmware file exceeds the maximum size of " + maxSizeBytes + " bytes.");
        }
        byte[] binary;
        try {
            binary = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidFirmwareBinaryException("Firmware file could not be read.");
        }
        if (binary.length > maxSizeBytes) {
            throw new InvalidFirmwareBinaryException(
                    "Firmware file exceeds the maximum size of " + maxSizeBytes + " bytes.");
        }
        if (binary.length < MIN_IMAGE_BYTES) {
            throw new InvalidFirmwareBinaryException(
                    "Firmware file is too small to be an ESP32 image (minimum " + MIN_IMAGE_BYTES + " bytes).");
        }
        if (binary[0] != ESP_IMAGE_MAGIC) {
            throw new InvalidFirmwareBinaryException("Firmware file is not an ESP32 image (missing 0xE9 magic byte).");
        }
        return binary;
    }

    private static void ensureVersionIncreases(
            Long modelId, FirmwareVersionNumber version, List<FirmwareVersionEntity> existing) {
        FirmwareVersionNumber highest = existing.stream()
                .map(release -> FirmwareVersionNumber.tryParse(release.getVersion()))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        boolean duplicate = existing.stream().anyMatch(release -> version.toString().equals(release.getVersion()));
        if (duplicate || (highest != null && version.compareTo(highest) <= 0)) {
            throw new FirmwareVersionConflictException(String.format(
                    "Version %s must be new and higher than %s for LED model %d.", version, highest, modelId));
        }
    }

    /** Covers failures after this method returns too (flush or commit), not just the ones caught here. */
    private void deleteOnRollback(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    binaryStorage.delete(objectKey);
                }
            }
        });
    }

    static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
