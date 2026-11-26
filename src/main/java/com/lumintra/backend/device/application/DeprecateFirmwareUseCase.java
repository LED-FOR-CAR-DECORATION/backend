package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.DeprecateFirmwareRequest;
import com.lumintra.backend.device.api.response.DeprecateFirmwareResponse;
import com.lumintra.backend.device.api.response.FirmwareReleaseResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.FirmwareVersionNumber;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * DEV-13: an ADMIN withdraws a faulty firmware release. If it was the model's ACTIVE release, the
 * highest-version SUPERSEDED release is promoted back to ACTIVE (the rollback target the admin page
 * previews). Deprecation is permanent; nothing is deleted (BR-FL4-05).
 */
@Service
@RequiredArgsConstructor
public class DeprecateFirmwareUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeprecateFirmwareUseCase.class);
    private static final Set<String> ADMIN_ROLE = Set.of("ADMIN");

    /** Highest semantic version first; unparseable legacy versions sort last, then newest upload. */
    private static final Comparator<FirmwareVersionEntity> ROLLBACK_PRIORITY = Comparator
            .comparing((FirmwareVersionEntity release) -> FirmwareVersionNumber.tryParse(release.getVersion()),
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(FirmwareVersionEntity::getUploadedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .reversed();

    private final FirmwareVersionRepository firmwareVersionRepository;
    private final LedModelRepository ledModelRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final InternalEmployeeAccessService employeeAccess;
    private final EmployeeLookupService employeeLookup;
    private final FirmwareReleaseMapper mapper;

    @Transactional
    public DeprecateFirmwareResponse execute(String subject, Long firmwareId, DeprecateFirmwareRequest request) {
        Long adminId = employeeAccess.requireActiveEmployeeId(subject, ADMIN_ROLE);
        String reason = request.reason().strip();

        // Lock the model first (same order as DEV-12 upload), then read the release rows fresh under the lock.
        Long modelId = firmwareVersionRepository.findModelIdByFirmwareId(firmwareId)
                .orElseThrow(() -> new FirmwareNotFoundException(firmwareId));
        LedModelEntity model = ledModelRepository.findByIdForUpdate(modelId).orElse(null);
        List<FirmwareVersionEntity> releases = firmwareVersionRepository.findByModelId(modelId);
        FirmwareVersionEntity target = releases.stream()
                .filter(release -> firmwareId.equals(release.getFirmwareId()))
                .findFirst()
                .orElseThrow(() -> new FirmwareNotFoundException(firmwareId));
        if (target.getStatus() == FirmwareStatus.DEPRECATED) {
            throw new FirmwareAlreadyDeprecatedException(firmwareId);
        }

        boolean wasActive = target.getStatus() == FirmwareStatus.ACTIVE;
        target.deprecate(adminId, reason, Instant.now());

        FirmwareVersionEntity promoted = null;
        if (wasActive) {
            promoted = releases.stream()
                    .filter(release -> release.getStatus() == FirmwareStatus.SUPERSEDED)
                    .min(ROLLBACK_PRIORITY)
                    .orElse(null);
            if (promoted != null) {
                promoted.reactivate();
            }
        }
        List<FirmwareVersionEntity> changed = new ArrayList<>();
        changed.add(target);
        if (promoted != null) {
            changed.add(promoted);
        }
        firmwareVersionRepository.saveAllAndFlush(changed);

        Map<Long, Long> installed = new HashMap<>();
        ledInstanceRepository.countBoundDevicesByFirmwareIds(changed.stream().map(FirmwareVersionEntity::getFirmwareId).toList())
                .forEach(count -> installed.put(count.getFirmwareId(), count.getDeviceCount()));
        Set<Long> employeeIds = new HashSet<>();
        changed.forEach(release -> {
            employeeIds.add(release.getUploadedByEmployeeId());
            employeeIds.add(release.getDeprecatedByEmployeeId());
        });
        employeeIds.remove(null);
        Map<Long, EmployeeProfileSnapshot> employees = employeeLookup.findProfilesByEmployeeIds(employeeIds);
        String modelName = model != null ? model.getModelName() : null;

        long affected = installed.getOrDefault(target.getFirmwareId(), 0L);
        FirmwareReleaseResponse deprecated = toResponse(target, modelName, employees, installed);
        FirmwareReleaseResponse newActive = promoted == null ? null : toResponse(promoted, modelName, employees, installed);

        log.info("Firmware deprecated [firmwareId={}] [modelId={}] [version={}] [wasActive={}] [promotedFirmwareId={}] [affectedDevices={}] [adminId={}]",
                firmwareId, modelId, target.getVersion(), wasActive,
                promoted != null ? promoted.getFirmwareId() : null, affected, adminId);
        return new DeprecateFirmwareResponse(deprecated, newActive, affected);
    }

    private FirmwareReleaseResponse toResponse(
            FirmwareVersionEntity release,
            String modelName,
            Map<Long, EmployeeProfileSnapshot> employees,
            Map<Long, Long> installed) {
        return mapper.toResponse(release, modelName,
                nameOf(employees, release.getUploadedByEmployeeId()),
                nameOf(employees, release.getDeprecatedByEmployeeId()),
                installed.getOrDefault(release.getFirmwareId(), 0L));
    }

    private static String nameOf(Map<Long, EmployeeProfileSnapshot> employees, Long employeeId) {
        EmployeeProfileSnapshot employee = employeeId == null ? null : employees.get(employeeId);
        return employee != null ? Objects.toString(employee.fullName(), null) : null;
    }
}
