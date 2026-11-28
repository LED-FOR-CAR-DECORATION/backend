package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.commerce.domain.model.FulfillmentDeviceSnapshot;
import com.lumintra.backend.commerce.domain.service.LedInstanceFulfillmentPort;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.commerce.OrderFulfillmentException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LedInstanceFulfillmentPortImpl implements LedInstanceFulfillmentPort {

        private static final Logger log = LoggerFactory.getLogger(LedInstanceFulfillmentPortImpl.class);

        private final LedInstanceRepository ledInstanceRepository;
        private final LedModelRepository ledModelRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findModelIdByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return ledModelRepository.findByProductId(productId).map(LedModelEntity::getModelId);
    }

    @Override
    @Transactional
    public FulfillmentDeviceSnapshot linkAndMarkInTransit(
            String serialNumber,
            Long expectedModelId) {
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new OrderFulfillmentException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "serialNumber must be provided."
            );
        }

        String trimmed = serialNumber.strip();
        LedInstanceEntity instance = ledInstanceRepository.findBySerialNumber(trimmed)
                .orElseThrow(() -> new OrderFulfillmentException(
                        HttpStatus.NOT_FOUND,
                        "DEVICE_NOT_FOUND",
                        "LED instance not found for serial number: " + trimmed + "."
                ));

        if (instance.getStatus() != LedInstanceStatus.IN_STOCK) {
            log.warn("LED instance link rejected: not in IN_STOCK [instanceId={}] [status={}]",
                    instance.getInstanceId(), instance.getStatus());
            throw new OrderFulfillmentException(
                    HttpStatus.CONFLICT,
                    "DEVICE_NOT_IN_STOCK",
                    "LED instance is not in IN_STOCK status (current: " + instance.getStatus() + ")."
            );
        }

        if (expectedModelId != null && !Objects.equals(instance.getModelId(), expectedModelId)) {
            log.warn("LED instance link rejected: model mismatch [instanceId={}] [actualModelId={}] [expectedModelId={}]",
                    instance.getInstanceId(), instance.getModelId(), expectedModelId);
            throw new OrderFulfillmentException(
                    HttpStatus.CONFLICT,
                    "HARDWARE_MODEL_MISMATCH",
                    "LED instance model " + instance.getModelId() + " does not match expected model " + expectedModelId + "."
            );
        }

        Instant now = Instant.now();
        int updatedRows = ledInstanceRepository.updateStatusIfCurrent(
                instance.getInstanceId(),
                LedInstanceStatus.IN_STOCK,
                LedInstanceStatus.IN_TRANSIT,
                now
        );

        if (updatedRows == 0) {
            log.warn("LED instance concurrent state conflict [instanceId={}]", instance.getInstanceId());
            throw new OrderFulfillmentException(
                    HttpStatus.CONFLICT,
                    "DEVICE_NOT_IN_STOCK",
                    "LED instance is no longer in IN_STOCK status."
            );
        }

        String modelName = instance.getModelId() != null
                ? ledModelRepository.findById(instance.getModelId()).map(LedModelEntity::getModelName).orElse(null)
                : null;
        if (modelName == null) {
            modelName = "LED Model #" + instance.getModelId();
        }

        log.info("LED instance successfully reserved and transitioned to IN_TRANSIT [instanceId={}] [serialNumber={}] [modelId={}]",
                instance.getInstanceId(), instance.getSerialNumber(), instance.getModelId());

        return new FulfillmentDeviceSnapshot(
                instance.getInstanceId(),
                instance.getModelId(),
                modelName,
                instance.getSerialNumber(),
                instance.getMacAddress(),
                LedInstanceStatus.IN_TRANSIT.name()
        );
    }

    @Override
    @Transactional
    public void releaseToStock(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        int rows = ledInstanceRepository.updateStatusIfCurrent(
                instanceId,
                LedInstanceStatus.IN_TRANSIT,
                LedInstanceStatus.IN_STOCK,
                Instant.now());
        log.info("Released LED instance back to IN_STOCK [instanceId={}] [updatedRows={}]", instanceId, rows);
    }
}
