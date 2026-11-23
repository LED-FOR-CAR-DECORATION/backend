package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.OtaCheckResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.FirmwareVersionNumber;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.OtaVersionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.shared.exception.device.DeviceNotBoundException;
import com.lumintra.backend.shared.exception.device.DeviceNotFoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * DEV-10: tells a customer whether their LED device should install its model's ACTIVE firmware.
 * Compares with "not equal" so a rollback after DEV-13 is offered too. Read-only: the device's
 * recorded firmware is only changed by the installed-firmware report (DEV-10B).
 */
@Service
@RequiredArgsConstructor
public class CheckFirmwareUpdateUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckFirmwareUpdateUseCase.class);

    private final LedInstanceRepository ledInstanceRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;

    @Transactional(readOnly = true)
    public OtaCheckResponse execute(Long customerId, Long deviceId, String reportedVersion) {
        LedInstanceEntity device = ledInstanceRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("LED device with id " + deviceId + " not found."));
        if (!Objects.equals(device.getOwnerCustomerId(), customerId)) {
            log.warn("OTA check ownership mismatch [customerId={}] [deviceId={}]", customerId, deviceId);
            throw new DeviceOwnershipMismatchException("You do not own this LED hardware device.");
        }
        if (device.getStatus() != LedInstanceStatus.BOUND) {
            throw new DeviceNotBoundException("LED device " + deviceId + " is " + device.getStatus() + ", not BOUND.");
        }

        List<FirmwareVersionEntity> releases = firmwareVersionRepository.findByModelId(device.getModelId());
        String currentVersion = resolveCurrentVersion(device, reportedVersion);
        OtaVersionStatus currentStatus = currentVersion == null ? OtaVersionStatus.UNKNOWN : releases.stream()
                .filter(release -> sameVersion(release.getVersion(), currentVersion))
                .findFirst()
                .map(release -> OtaVersionStatus.of(release.getStatus()))
                .orElse(OtaVersionStatus.UNKNOWN);

        FirmwareVersionEntity active = releases.stream()
                .filter(release -> release.getStatus() == FirmwareStatus.ACTIVE)
                .max(Comparator.comparing(FirmwareVersionEntity::getUploadedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        boolean updateAvailable = active != null
                && (currentVersion == null || !sameVersion(active.getVersion(), currentVersion));
        boolean rollback = updateAvailable && currentVersion != null
                && (currentStatus == OtaVersionStatus.DEPRECATED || isHigher(currentVersion, active.getVersion()));

        OtaCheckResponse.TargetFirmware target = !updateAvailable ? null : OtaCheckResponse.TargetFirmware.builder()
                .firmwareId(active.getFirmwareId())
                .version(active.getVersion())
                .changelog(active.getChangelog())
                .fileSizeBytes(active.getFileSizeBytes() != null ? active.getFileSizeBytes() : 0L)
                .sha256(active.getSha256())
                .build();

        log.info("OTA check [customerId={}] [deviceId={}] [currentVersion={}] [currentStatus={}] [activeVersion={}] [updateAvailable={}] [rollback={}]",
                customerId, deviceId, currentVersion, currentStatus,
                active != null ? active.getVersion() : null, updateAvailable, rollback);
        return OtaCheckResponse.builder()
                .deviceId(device.getInstanceId())
                .currentVersion(currentVersion)
                .currentVersionStatus(currentStatus)
                .updateAvailable(updateAvailable)
                .isRollback(rollback)
                .targetFirmware(target)
                .build();
    }

    /** BLE-reported version first, then the recorded firmware's version, else unknown (null). */
    private String resolveCurrentVersion(LedInstanceEntity device, String reportedVersion) {
        if (reportedVersion != null && !reportedVersion.isBlank()) {
            return FirmwareVersionNumber.parse(reportedVersion).toString();
        }
        if (device.getCurrentFirmwareId() == null) {
            return null;
        }
        return firmwareVersionRepository.findById(device.getCurrentFirmwareId())
                .map(FirmwareVersionEntity::getVersion)
                .orElse(null);
    }

    private static boolean sameVersion(String first, String second) {
        Optional<FirmwareVersionNumber> a = Optional.ofNullable(FirmwareVersionNumber.tryParse(first));
        Optional<FirmwareVersionNumber> b = Optional.ofNullable(FirmwareVersionNumber.tryParse(second));
        if (a.isPresent() && b.isPresent()) {
            return a.get().compareTo(b.get()) == 0;
        }
        return Objects.equals(first, second);
    }

    private static boolean isHigher(String candidate, String reference) {
        FirmwareVersionNumber a = FirmwareVersionNumber.tryParse(candidate);
        FirmwareVersionNumber b = FirmwareVersionNumber.tryParse(reference);
        return a != null && b != null && a.compareTo(b) > 0;
    }
}
