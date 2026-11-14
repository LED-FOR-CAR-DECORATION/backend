package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.ClaimDeviceRequest;
import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.VerifiedDeviceQrPayload;
import com.lumintra.backend.device.domain.service.FactoryQrPayloadService;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.DeviceAlreadyBoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.exception.device.OrderNotDeliveredYetException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClaimDeviceUseCase {

    public static final long WARRANTY_DURATION_DAYS = 180L;

    private final FactoryQrPayloadService factoryQrPayloadService;
    private final LedInstanceRepository ledInstanceRepository;
    private final LedModelRepository ledModelRepository;
    private final OrderFulfillmentLookupService orderFulfillmentLookupService;
    private final LedInstanceMapper ledInstanceMapper;

    @Transactional
    public ClaimDeviceResponse execute(Long customerId, ClaimDeviceRequest request) {
        VerifiedDeviceQrPayload verifiedQr = factoryQrPayloadService.parseAndVerifyQrPayload(
                request != null ? request.qrPayload() : null);

        LedInstanceEntity instance = ledInstanceRepository
                .findBySerialNumberAndMacAddress(verifiedQr.serialNumber(), verifiedQr.macAddress())
                .orElseThrow(() -> new LedInstanceNotFoundException(
                        "LED instance not found for serial number: " + verifiedQr.serialNumber()));

        if (instance.getStatus() == LedInstanceStatus.BOUND) {
            throw new DeviceAlreadyBoundException("Device is already bound to a customer account.");
        }

        InstanceFulfillmentSnapshot fulfillment = orderFulfillmentLookupService
                .findFulfillmentByInstanceId(instance.getInstanceId())
                .orElse(null);

        if (fulfillment == null || !Objects.equals(fulfillment.orderCustomerId(), customerId)) {
            throw new DeviceOwnershipMismatchException(
                    "This device does not belong to an order owned by your account.");
        }

        if (!fulfillment.isOrderDeliveredOrCompleted()) {
            throw new OrderNotDeliveredYetException(
                    "Order must be DELIVERED or COMPLETED before claiming this device.");
        }

        LedModelEntity modelEntity = instance.getModelId() != null
                ? ledModelRepository.findById(instance.getModelId()).orElse(null)
                : null;

        String resolvedDeviceName = resolveDeviceName(request.deviceName(), modelEntity, instance);
        Instant boundAt = Instant.now();
        Instant warrantyExpiresAt = boundAt.plus(WARRANTY_DURATION_DAYS, ChronoUnit.DAYS);

        int updatedRows = ledInstanceRepository.bindIfUnbound(
                instance.getInstanceId(),
                customerId,
                resolvedDeviceName,
                boundAt,
                warrantyExpiresAt);

        if (updatedRows == 0) {
            throw new DeviceAlreadyBoundException("Device is already bound to a customer account.");
        }

        instance.bindToCustomer(customerId, resolvedDeviceName, boundAt, warrantyExpiresAt);
        return ledInstanceMapper.toClaimResponse(instance, modelEntity);
    }

    private String resolveDeviceName(
            String requestedDeviceName,
            LedModelEntity modelEntity,
            LedInstanceEntity instance) {
        if (requestedDeviceName != null && !requestedDeviceName.isBlank()) {
            return requestedDeviceName.strip();
        }
        if (modelEntity != null && modelEntity.getModelName() != null && !modelEntity.getModelName().isBlank()) {
            return modelEntity.getModelName();
        }
        return instance.getSerialNumber();
    }
}
