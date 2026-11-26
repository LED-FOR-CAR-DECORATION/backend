package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.LedModelListResponse.ActiveFirmwareSummary;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the ACTIVE firmware summary for a set of LED models with two queries in total
 * (active releases, then installed-device counts), independent of the number of models.
 */
@Component
@RequiredArgsConstructor
public class ActiveFirmwareSummaryResolver {

    /** If several rows are ACTIVE for one model (should not happen), the newest upload wins. */
    private static final Comparator<FirmwareVersionEntity> NEWEST_FIRST = Comparator
            .comparing(FirmwareVersionEntity::getUploadedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(FirmwareVersionEntity::getFirmwareId, Comparator.nullsFirst(Comparator.naturalOrder()))
            .reversed();

    private final FirmwareVersionRepository firmwareVersionRepository;
    private final LedInstanceRepository ledInstanceRepository;

    /** @return modelId to summary; models without an ACTIVE release are absent from the map. */
    public Map<Long, ActiveFirmwareSummary> resolve(Collection<Long> modelIds) {
        List<Long> ids = modelIds == null ? List.of() : modelIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, FirmwareVersionEntity> activeByModel = new HashMap<>();
        firmwareVersionRepository.findByModelIdInAndStatus(ids, FirmwareStatus.ACTIVE).stream()
                .sorted(NEWEST_FIRST)
                .forEach(firmware -> activeByModel.putIfAbsent(firmware.getModelId(), firmware));
        if (activeByModel.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> installedByFirmware = new HashMap<>();
        List<Long> firmwareIds = activeByModel.values().stream().map(FirmwareVersionEntity::getFirmwareId).toList();
        for (LedInstanceRepository.FirmwareInstallCount count
                : ledInstanceRepository.countBoundDevicesByFirmwareIds(firmwareIds)) {
            installedByFirmware.put(count.getFirmwareId(), count.getDeviceCount());
        }

        Map<Long, ActiveFirmwareSummary> summaries = new HashMap<>();
        activeByModel.forEach((modelId, firmware) -> summaries.put(modelId, ActiveFirmwareSummary.builder()
                .firmwareId(firmware.getFirmwareId())
                .version(firmware.getVersion())
                .uploadedAt(firmware.getUploadedAt())
                .installedDeviceCount(installedByFirmware.getOrDefault(firmware.getFirmwareId(), 0L))
                .build()));
        return summaries;
    }

    public ActiveFirmwareSummary resolve(Long modelId) {
        return modelId == null ? null : resolve(List.of(modelId)).get(modelId);
    }
}
