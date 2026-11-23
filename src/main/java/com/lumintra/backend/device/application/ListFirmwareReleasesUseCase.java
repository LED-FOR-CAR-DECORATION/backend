package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.FirmwareFilterRequest;
import com.lumintra.backend.device.api.response.FirmwareReleaseResponse;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.FirmwareReleaseMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionSpecifications;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.shared.response.PaginationMeta;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DEV-11: paginated firmware release catalog. Model names, admin names and installed-device counts
 * are loaded in one batch query each for the whole page.
 */
@Service
@RequiredArgsConstructor
public class ListFirmwareReleasesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListFirmwareReleasesUseCase.class);

    private final FirmwareVersionRepository firmwareVersionRepository;
    private final LedModelRepository ledModelRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final EmployeeLookupService employeeLookup;
    private final FirmwareReleaseMapper mapper;

    public record Result(List<FirmwareReleaseResponse> items, PaginationMeta meta) {
    }

    @Transactional(readOnly = true)
    public Result execute(FirmwareFilterRequest request) {
        FirmwareFilterRequest filter = request != null ? request : new FirmwareFilterRequest(null, null, null, null);
        PageRequest pageable = PageRequest.of(filter.page(), filter.size(),
                Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("firmwareId")));
        Page<FirmwareVersionEntity> page = firmwareVersionRepository.findAll(
                FirmwareVersionSpecifications.withFilter(filter), pageable);
        List<FirmwareVersionEntity> releases = page.getContent();

        Map<Long, String> modelNames = releases.isEmpty() ? Map.of() : ledModelRepository
                .findAllById(releases.stream().map(FirmwareVersionEntity::getModelId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(LedModelEntity::getModelId, LedModelEntity::getModelName));

        Set<Long> employeeIds = new HashSet<>();
        releases.forEach(release -> {
            employeeIds.add(release.getUploadedByEmployeeId());
            employeeIds.add(release.getDeprecatedByEmployeeId());
        });
        employeeIds.remove(null);
        Map<Long, EmployeeProfileSnapshot> employees = employeeIds.isEmpty()
                ? Map.of()
                : employeeLookup.findProfilesByEmployeeIds(employeeIds);

        Map<Long, Long> installed = new HashMap<>();
        if (!releases.isEmpty()) {
            ledInstanceRepository.countBoundDevicesByFirmwareIds(releases.stream()
                            .map(FirmwareVersionEntity::getFirmwareId)
                            .filter(Objects::nonNull)
                            .toList())
                    .forEach(count -> installed.put(count.getFirmwareId(), count.getDeviceCount()));
        }

        List<FirmwareReleaseResponse> items = releases.stream()
                .map(release -> mapper.toResponse(
                        release,
                        modelNames.get(release.getModelId()),
                        nameOf(employees, release.getUploadedByEmployeeId()),
                        nameOf(employees, release.getDeprecatedByEmployeeId()),
                        installed.getOrDefault(release.getFirmwareId(), 0L)))
                .toList();

        log.info("Listed firmware releases [page={}] [size={}] [modelId={}] [status={}] [totalElements={}]",
                filter.page(), filter.size(), filter.modelId(), filter.status(), page.getTotalElements());
        return new Result(items, PaginationMeta.from(page));
    }

    private static String nameOf(Map<Long, EmployeeProfileSnapshot> employees, Long employeeId) {
        EmployeeProfileSnapshot employee = employeeId == null ? null : employees.get(employeeId);
        return employee != null ? employee.fullName() : null;
    }
}
