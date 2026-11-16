package com.lumintra.backend.device.infrastructure.persistence.mapper;

import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.api.response.CustomerDeviceDetailResponse;
import com.lumintra.backend.device.api.response.CustomerDeviceListResponse;
import com.lumintra.backend.device.api.response.LedInstanceDetailResponse;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.service.FactoryQrPayloadService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.function.BiFunction;

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
                .fulfillment(mapFulfillment(fulfillmentSnapshot,
                        (orderId, orderItemId) -> LedInstanceListResponse.FulfillmentInfo.builder()
                                .orderId(orderId)
                                .orderItemId(orderItemId)
                                .build()))
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

    public CustomerDeviceListResponse.CustomerDeviceItem toCustomerDeviceItem(
            LedInstanceEntity entity,
            LedModelEntity modelEntity,
            Instant asOf) {
        if (entity == null) {
            return null;
        }

        Long resolvedModelId = modelEntity != null && modelEntity.getModelId() != null
                ? modelEntity.getModelId()
                : entity.getModelId();
        String resolvedModelName = modelEntity != null ? modelEntity.getModelName() : null;
        LedModelListResponse.LedSpec resolvedSpec = modelEntity != null ? ledModelMapper.toSpec(modelEntity) : null;

        return CustomerDeviceListResponse.CustomerDeviceItem.builder()
                .deviceId(entity.getInstanceId())
                .serialNumber(entity.getSerialNumber())
                .macAddress(entity.getMacAddress())
                .deviceName(entity.getDeviceName())
                .status(entity.getStatus())
                .modelId(resolvedModelId)
                .modelName(resolvedModelName)
                .widthPx(resolvedSpec != null ? resolvedSpec.widthPx() : null)
                .heightPx(resolvedSpec != null ? resolvedSpec.heightPx() : null)
                .currentFirmwareId(entity.getCurrentFirmwareId())
                .boundAt(entity.getBoundAt())
                .warrantyExpiresAt(entity.getWarrantyExpiresAt())
                .warrantyStatus(entity.resolveWarrantyStatus(asOf))
                .build();
    }

    public CustomerDeviceDetailResponse toCustomerDeviceDetail(
            LedInstanceEntity entity,
            LedModelEntity modelEntity,
            InstanceFulfillmentSnapshot fulfillmentSnapshot,
            Instant asOf) {
        if (entity == null) {
            return null;
        }

        return CustomerDeviceDetailResponse.builder()
                .deviceId(entity.getInstanceId())
                .serialNumber(entity.getSerialNumber())
                .macAddress(entity.getMacAddress())
                .deviceName(entity.getDeviceName())
                .status(entity.getStatus())
                .currentFirmwareId(entity.getCurrentFirmwareId())
                .boundAt(entity.getBoundAt())
                .warrantyExpiresAt(entity.getWarrantyExpiresAt())
                .warrantyStatus(entity.resolveWarrantyStatus(asOf))
                .model(mapModelInfo(entity, modelEntity,
                        (modelId, productId, modelName, status, spec) -> CustomerDeviceDetailResponse.ModelInfo.builder()
                                .modelId(modelId)
                                .productId(productId)
                                .modelName(modelName)
                                .status(status)
                                .spec(spec)
                                .build()))
                .fulfillment(mapFulfillment(fulfillmentSnapshot,
                        (orderId, orderItemId) -> CustomerDeviceDetailResponse.FulfillmentInfo.builder()
                                .orderId(orderId)
                                .orderItemId(orderItemId)
                                .build()))
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
                .model(mapModelInfo(entity, modelEntity,
                        (modelId, productId, modelName, status, spec) -> LedInstanceDetailResponse.ModelInfo.builder()
                                .modelId(modelId)
                                .productId(productId)
                                .modelName(modelName)
                                .status(status)
                                .spec(spec)
                                .build()))
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
                .fulfillment(mapFulfillment(fulfillmentSnapshot,
                        (orderId, orderItemId) -> LedInstanceDetailResponse.FulfillmentInfo.builder()
                                .orderId(orderId)
                                .orderItemId(orderItemId)
                                .build()))
                .owner(toDetailOwner(entity.getOwnerCustomerId(), ownerProfile))
                .qrPayload(resolveQrPayload(entity))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private <T> T mapModelInfo(LedInstanceEntity entity, LedModelEntity modelEntity, ModelInfoFactory<T> factory) {
        if (modelEntity == null) {
            return factory.create(entity.getModelId(), null, null, null, null);
        }
        Long resolvedModelId = modelEntity.getModelId() != null ? modelEntity.getModelId() : entity.getModelId();
        return factory.create(
                resolvedModelId,
                modelEntity.getProductId(),
                modelEntity.getModelName(),
                ledModelMapper.resolveStatus(modelEntity),
                ledModelMapper.toSpec(modelEntity)
        );
    }

    private <T> T mapFulfillment(InstanceFulfillmentSnapshot snapshot, BiFunction<Long, Long, T> factory) {
        if (!hasFulfillmentData(snapshot)) {
            return null;
        }
        return factory.apply(snapshot.orderId(), snapshot.orderItemId());
    }

    private boolean hasFulfillmentData(InstanceFulfillmentSnapshot snapshot) {
        return snapshot != null && (snapshot.orderId() != null || snapshot.orderItemId() != null);
    }

    @FunctionalInterface
    private interface ModelInfoFactory<T> {
        T create(Long modelId, Long productId, String modelName, LedModelStatus status, LedModelListResponse.LedSpec spec);
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
