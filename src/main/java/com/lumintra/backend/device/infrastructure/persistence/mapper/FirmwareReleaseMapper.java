package com.lumintra.backend.device.infrastructure.persistence.mapper;

import com.lumintra.backend.device.api.response.FirmwareReleaseResponse;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import org.springframework.stereotype.Component;

/** Maps firmware rows to the admin API shape shared by DEV-11 (list), DEV-12 (upload) and DEV-13 (deprecate). */
@Component
public class FirmwareReleaseMapper {

    public FirmwareReleaseResponse toResponse(
            FirmwareVersionEntity entity,
            String modelName,
            String uploadedBy,
            String deprecatedBy,
            long installedDeviceCount) {
        return FirmwareReleaseResponse.builder()
                .firmwareId(entity.getFirmwareId())
                .modelId(entity.getModelId())
                .modelName(modelName)
                .version(entity.getVersion())
                .status(entity.getStatus())
                .changelog(entity.getChangelog())
                .fileSizeBytes(entity.getFileSizeBytes() != null ? entity.getFileSizeBytes() : 0L)
                .sha256(entity.getSha256())
                .uploadedBy(uploadedBy)
                .uploadedAt(entity.getUploadedAt())
                .deprecatedAt(entity.getDeprecatedAt())
                .deprecatedBy(deprecatedBy)
                .deprecationReason(entity.getDeprecationReason())
                .installedDeviceCount(installedDeviceCount)
                .build();
    }
}
