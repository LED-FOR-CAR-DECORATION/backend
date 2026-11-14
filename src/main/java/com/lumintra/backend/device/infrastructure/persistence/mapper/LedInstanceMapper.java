package com.lumintra.backend.device.infrastructure.persistence.mapper;

import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.FactoryQrPayloadService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Maps persisted {@link LedInstanceEntity} rows and cross-module snapshots into list response items.
 */
@Component
@RequiredArgsConstructor
public class LedInstanceMapper {

    private final FactoryQrPayloadService factoryQrPayloadService;

    public LedInstanceListResponse.LedInstanceItem toItem(
            LedInstanceEntity entity,
            Map<Long, LedModelSnapshot> modelsById,
            Map<Long, CustomerProfileSnapshot> ownersById,
            Map<Long, InstanceFulfillmentSnapshot> fulfillmentsByInstanceId) {
        LedModelSnapshot model = entity.getModelId() != null && modelsById != null
                ? modelsById.get(entity.getModelId())
                : null;

        LedInstanceListResponse.FulfillmentInfo fulfillment = resolveFulfillment(
                entity.getInstanceId(), fulfillmentsByInstanceId);

        LedInstanceListResponse.OwnerInfo owner = resolveOwner(
                entity.getOwnerCustomerId(), ownersById);

        String qrPayload = resolveQrPayload(entity);

        return LedInstanceListResponse.LedInstanceItem.builder()
                .instanceId(entity.getInstanceId())
                .modelId(entity.getModelId())
                .modelName(model != null ? model.modelName() : null)
                .serialNumber(entity.getSerialNumber())
                .macAddress(entity.getMacAddress())
                .hardwareRevision(entity.getHardwareRevision())
                .deviceName(entity.getDeviceName())
                .status(entity.getStatus())
                .fulfillment(fulfillment)
                .owner(owner)
                .boundAt(entity.getBoundAt())
                .warrantyExpiresAt(entity.getWarrantyExpiresAt())
                .manufacturedAt(entity.getManufacturedAt())
                .qrPayload(qrPayload)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private LedInstanceListResponse.FulfillmentInfo resolveFulfillment(
            Long instanceId,
            Map<Long, InstanceFulfillmentSnapshot> fulfillmentsByInstanceId) {
        if (instanceId == null || fulfillmentsByInstanceId == null) {
            return null;
        }
        InstanceFulfillmentSnapshot snapshot = fulfillmentsByInstanceId.get(instanceId);
        if (snapshot == null || (snapshot.orderId() == null && snapshot.orderItemId() == null)) {
            return null;
        }
        return LedInstanceListResponse.FulfillmentInfo.builder()
                .orderId(snapshot.orderId())
                .orderItemId(snapshot.orderItemId())
                .build();
    }

    private LedInstanceListResponse.OwnerInfo resolveOwner(
            Long ownerCustomerId,
            Map<Long, CustomerProfileSnapshot> ownersById) {
        if (ownerCustomerId == null) {
            return null;
        }
        CustomerProfileSnapshot profile = ownersById != null ? ownersById.get(ownerCustomerId) : null;
        return LedInstanceListResponse.OwnerInfo.builder()
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
