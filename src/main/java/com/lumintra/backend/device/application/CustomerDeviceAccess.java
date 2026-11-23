package com.lumintra.backend.device.application;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.shared.exception.device.DeviceNotBoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Shared device checks for the customer OTA endpoints (DEV-10, DEV-21, DEV-10B):
 * 404 LED_INSTANCE_NOT_FOUND, 403 DEVICE_OWNERSHIP_MISMATCH, 409 DEVICE_NOT_BOUND.
 */
@Component
@RequiredArgsConstructor
public class CustomerDeviceAccess {

    private static final Logger log = LoggerFactory.getLogger(CustomerDeviceAccess.class);

    private final LedInstanceRepository ledInstanceRepository;

    public LedInstanceEntity requireOwnedBoundDevice(Long customerId, Long deviceId) {
        return check(customerId, deviceId, ledInstanceRepository.findById(deviceId));
    }

    /** Same checks, with the device row locked for the rest of the transaction. */
    public LedInstanceEntity requireOwnedBoundDeviceForUpdate(Long customerId, Long deviceId) {
        return check(customerId, deviceId, ledInstanceRepository.findByIdForUpdate(deviceId));
    }

    private static LedInstanceEntity check(Long customerId, Long deviceId, Optional<LedInstanceEntity> found) {
        LedInstanceEntity device = found
                .orElseThrow(() -> new LedInstanceNotFoundException("LED hardware device not found."));
        if (!Objects.equals(device.getOwnerCustomerId(), customerId)) {
            log.warn("Customer device ownership mismatch [customerId={}] [deviceId={}]", customerId, deviceId);
            throw new DeviceOwnershipMismatchException("You do not own this LED hardware device.");
        }
        if (device.getStatus() != LedInstanceStatus.BOUND) {
            throw new DeviceNotBoundException("LED device " + deviceId + " is " + device.getStatus() + ", not BOUND.");
        }
        return device;
    }
}
