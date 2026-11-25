package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.ReportInstalledFirmwareRequest;
import com.lumintra.backend.device.api.response.InstalledFirmwareResponse;
import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.shared.exception.device.FirmwareModelMismatchException;
import com.lumintra.backend.shared.exception.device.FirmwareNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * DEV-10B: records the firmware release the app reports the device is running after an update.
 *
 * <p>This is the app's claim, not proof: the server cannot verify what is flashed on the device. Warranty
 * decisions about unofficial firmware (BR-FL5-05) must rely on inspecting the physical unit.
 */
@Service
@RequiredArgsConstructor
public class ReportInstalledFirmwareUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReportInstalledFirmwareUseCase.class);

    private final CustomerDeviceAccess deviceAccess;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final FirmwareOtaSessionRepository sessionRepository;

    @Transactional
    public InstalledFirmwareResponse execute(Long customerId, Long deviceId, ReportInstalledFirmwareRequest request) {
        // Row lock: serialises with session creation (DEV-21) for the same device.
        LedInstanceEntity device = deviceAccess.requireOwnedBoundDeviceForUpdate(customerId, deviceId);

        // Any status is accepted: the report records what is installed, even a release deprecated since.
        FirmwareVersionEntity firmware = firmwareVersionRepository.findById(request.firmwareId())
                .orElseThrow(() -> new FirmwareNotFoundException(request.firmwareId()));
        if (!Objects.equals(firmware.getModelId(), device.getModelId())) {
            throw new FirmwareModelMismatchException("Firmware " + firmware.getFirmwareId()
                    + " belongs to a different LED model than device " + device.getInstanceId() + ".");
        }

        if (Objects.equals(device.getCurrentFirmwareId(), firmware.getFirmwareId())) {
            log.info("Installed firmware unchanged [deviceId={}] [firmwareId={}]", device.getInstanceId(),
                    firmware.getFirmwareId());
            return toResponse(device, firmware);
        }

        Long previousFirmwareId = device.getCurrentFirmwareId();
        device.recordInstalledFirmware(firmware.getFirmwareId(), Instant.now());

        List<FirmwareOtaSessionEntity> completed = sessionRepository
                .findByLedInstanceIdAndStatus(device.getInstanceId(), OtaSessionStatus.ISSUED).stream()
                .filter(session -> Objects.equals(session.getFirmwareId(), firmware.getFirmwareId()))
                .toList();
        completed.forEach(FirmwareOtaSessionEntity::complete);

        log.info("Installed firmware reported [deviceId={}] [previousFirmwareId={}] [firmwareId={}] [version={}] "
                        + "[firmwareStatus={}] [completedSessions={}]",
                device.getInstanceId(), previousFirmwareId, firmware.getFirmwareId(), firmware.getVersion(),
                firmware.getStatus(), completed.size());
        return toResponse(device, firmware);
    }

    private static InstalledFirmwareResponse toResponse(LedInstanceEntity device, FirmwareVersionEntity firmware) {
        return InstalledFirmwareResponse.builder()
                .deviceId(device.getInstanceId())
                .firmwareId(firmware.getFirmwareId())
                .version(firmware.getVersion())
                .firmwareStatus(firmware.getStatus())
                .firmwareUpdatedAt(device.getFirmwareUpdatedAt())
                .build();
    }
}
