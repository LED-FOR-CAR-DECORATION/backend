package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.device.domain.service.DeviceOwnershipService;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DeviceOwnershipServiceImpl implements DeviceOwnershipService {

    private final LedInstanceRepository ledInstanceRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isDeviceOwnedByCustomer(Long deviceId, Long customerId) {
        if (deviceId == null || customerId == null) {
            return false;
        }
        return ledInstanceRepository.findById(deviceId)
                .map(instance -> Objects.equals(instance.getOwnerCustomerId(), customerId))
                .orElse(false);
    }
}
