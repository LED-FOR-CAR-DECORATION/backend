package com.lumintra.backend.device.infrastructure.persistence.mapper;

import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.api.response.LedInstanceDetailResponse;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.FactoryQrPayloadService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Maps persisted {@link LedInstanceEntity} rows and cross-module snapshots into inventory response DTOs.
 */
@Component
@RequiredArgsConstructor
public class LedInstanceMapper {

    private final FactoryQrPayloadService factoryQrPayloadService;
    private final LedModelMapper ledModelMapper;

    public LedInstanceListResponse.LedInstanceItem toItem(
            LedInstanceEntity entity,
            Map<Long, LedModelSnapshot> modelsById,
            Map<Long, CustomerProfileSnapshot> ownersById,
            Map<Long, InstanceFulfillmentSnapshot> fulfillmentsByInstanceId) {
        LedModelSnapshot model = entity.getModelId() != null && modelsById != null
                ? modelsById.get(entity.getModelId())
                : null;

        InstanceFulfillmentSnapshot fulfillmentSnapshot = entity.getInstanceId() != null && fulfillmentsByInstanceId != null
                ? fulfillmentsByInstanceId.get(entity.getInstanceId())
                : null;

        CustomerProfileSnapshot ownerProfile = entity.getOwnerCustomerId() != null && ownersById != null
                ? ownersById.get(entity.getOwnerCustomerId())
                : null;

        return LedInstanceListResponse.LedInstanceItem.builder()
                .instanceId(entity.getInstanceId())
                .modelId(entity.getModelId())
                .modelName(model != null ? model.modelName() : null)
                .serialNumber(entity.getSerialNumber())
                .macAddress(entity.getMacAddress())
                .hardwareRevision(entity.getHardwareRevision())
                .deviceName(entity.getDeviceName())
                .status(entity.getStatus())
                .fulfillment(toListFulfillment(fulfillmentSnapshot))
                .owner(toListOwner(entity.getOwnerCustomerId(), ownerProfile))
                .boundAt(entity.getBoundAt())
                .warrantyExpiresAt(entity.getWarrantyExpiresAt())
                .manufacturedAt(entity.getManufacturedAt())
                .qrPayload(resolveQrPayload(entity))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ClaimDeviceResponse toClaimResponse(LedInstanceEntity entity, LedModelEntity modelEntity) {
        Long resolvedModelId = modelEntity != null && modelEntity.getModelId() != null
                ? modelEntity.getModelId()
                : entity.getModelId();
        String resolvedModelName = modelEntity != null ? modelEntity.getModelName() : null;
        LedModelListResponse.LedSpec resolvedSpec = modelEntity != null
                ? ledModelMapper.toSpec(modelEntity)
                : LedModelListResponse.LedSpec.builder().attributes(java.util.List.of()).build();

        return ClaimDeviceResponse.builder()
                .deviceId(entity.getInstanceId())
                .serialNumber(entity.getSerialNumber())
                .macAddress(entity.getMacAddress())
                .deviceName(entity.getDeviceName())
                .modelId(resolvedModelId)
                .modelName(resolvedModelName)
                .spec(resolvedSpec)
                .boundAt(entity.getBoundAt())
                .warrantyExpiresAt(entity.getWarrantyExpiresAt())
                .build();
    }

    public LedInstanceDetailResponse toDetail(
            LedInstanceEntity entity,
            LedModelEntity modelEntity,
            CustomerProfileSnapshot ownerProfile,
            InstanceFulfillmentSnapshot fulfillmentSnapshot,
            Instant asOf) {
        return LedInstanceDetailResponse.builder()
                .instanceId(entity.getInstanceId())
                .model(toModelInfo(entity, modelEntity))
                .serialNumber(entity.getSerialNumber())
                .macAddress(entity.getMacAddress())
                .hardwareRevision(entity.getHardwareRevision())
                .currentFirmwareId(entity.getCurrentFirmwareId())
                .deviceName(entity.getDeviceName())
                .status(entity.getStatus())
                .warrantyStatus(entity.resolveWarrantyStatus(asOf))
                .boundAt(entity.getBoundAt())
                .warrantyExpiresAt(entity.getWarrantyExpiresAt())
                .manufacturedAt(entity.getManufacturedAt())
                .fulfillment(toDetailFulfillment(fulfillmentSnapshot))
                .owner(toDetailOwner(entity.getOwnerCustomerId(), ownerProfile))
                .qrPayload(resolveQrPayload(entity))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private LedInstanceDetailResponse.ModelInfo toModelInfo(LedInstanceEntity entity, LedModelEntity modelEntity) {
        if (modelEntity == null) {
            return LedInstanceDetailResponse.ModelInfo.builder()
                    .modelId(entity.getModelId())
                    .build();
        }
        return LedInstanceDetailResponse.ModelInfo.builder()
                .modelId(modelEntity.getModelId() != null ? modelEntity.getModelId() : entity.getModelId())
                .productId(modelEntity.getProductId())
                .modelName(modelEntity.getModelName())
                .status(ledModelMapper.resolveStatus(modelEntity))
                .spec(ledModelMapper.toSpec(modelEntity))
                .build();
    }

    private LedInstanceListResponse.FulfillmentInfo toListFulfillment(InstanceFulfillmentSnapshot snapshot) {
        if (!hasFulfillmentData(snapshot)) {
            return null;
        }
        return LedInstanceListResponse.FulfillmentInfo.builder()
                .orderId(snapshot.orderId())
                .orderItemId(snapshot.orderItemId())
                .build();
    }

    private LedInstanceDetailResponse.FulfillmentInfo toDetailFulfillment(InstanceFulfillmentSnapshot snapshot) {
        if (!hasFulfillmentData(snapshot)) {
            return null;
        }
        return LedInstanceDetailResponse.FulfillmentInfo.builder()
                .orderId(snapshot.orderId())
                .orderItemId(snapshot.orderItemId())
                .build();
    }

    private boolean hasFulfillmentData(InstanceFulfillmentSnapshot snapshot) {
        return snapshot != null && (snapshot.orderId() != null || snapshot.orderItemId() != null);
    }

    private LedInstanceListResponse.OwnerInfo toListOwner(Long ownerCustomerId, CustomerProfileSnapshot profile) {
        if (ownerCustomerId == null) {
            return null;
        }
        return LedInstanceListResponse.OwnerInfo.builder()
                .customerId(ownerCustomerId)
                .fullName(profile != null ? profile.fullName() : null)
                .phone(profile != null ? profile.phone() : null)
                .email(profile != null ? profile.email() : null)
                .build();
    }

    private LedInstanceDetailResponse.OwnerInfo toDetailOwner(Long ownerCustomerId, CustomerProfileSnapshot profile) {
        if (ownerCustomerId == null) {
            return null;
        }
        return LedInstanceDetailResponse.OwnerInfo.builder()
                .customerId(ownerCustomerId)
                .fullName(profile != null ? profile.fullName() : null)
                .phone(profile != null ? profile.phone() : null)
                .email(profile != null ? profile.email() : null)
                .build();
    }

    private String resolveQrPayload(LedInstanceEntity entity) {
        if (!entity.isQrReprintable()) {
            return null;
        }
        if (entity.getSerialNumber() == null || entity.getSerialNumber().isBlank()
                || entity.getMacAddress() == null || entity.getMacAddress().isBlank()) {
            return null;
        }
        return factoryQrPayloadService.generateQrPayload(entity.getSerialNumber(), entity.getMacAddress());
    }
}
