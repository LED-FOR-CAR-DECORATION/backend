package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.CustomerDeviceDetailResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * Use case for retrieving full hardware details, model specifications, warranty coverage,
 * and original purchase order linkage for a single device owned by the authenticated customer.
 */
@Service
@RequiredArgsConstructor
public class GetCustomerDeviceDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetCustomerDeviceDetailUseCase.class);

    private final LedInstanceRepository ledInstanceRepository;
    private final LedModelRepository ledModelRepository;
    private final OrderFulfillmentLookupService orderFulfillmentLookupService;
    private final LedInstanceMapper ledInstanceMapper;

    @Transactional(readOnly = true)
    public CustomerDeviceDetailResponse execute(Long customerId, Long deviceId) {
        if (customerId == null) {
            throw new InsufficientAuthenticationException("Authenticated customer identity is required.");
        }
        if (deviceId == null) {
            throw new LedInstanceNotFoundException("LED hardware device not found.");
        }

        LedInstanceEntity entity = ledInstanceRepository.findById(deviceId)
                .orElseThrow(() -> {
                    log.warn("Customer device detail not found [customerId={}] [deviceId={}]", customerId, deviceId);
                    return new LedInstanceNotFoundException("LED hardware device not found.");
                });

        if (entity.getOwnerCustomerId() == null || !Objects.equals(entity.getOwnerCustomerId(), customerId)) {
            log.warn("Customer device ownership mismatch [customerId={}] [deviceId={}] [ownerCustomerId={}]",
                    customerId, deviceId, entity.getOwnerCustomerId());
            throw new DeviceOwnershipMismatchException("You do not own this LED hardware device.");
        }

        LedModelEntity modelEntity = entity.getModelId() != null
                ? ledModelRepository.findById(entity.getModelId()).orElse(null)
                : null;

        InstanceFulfillmentSnapshot fulfillmentSnapshot = entity.getInstanceId() != null
                ? orderFulfillmentLookupService.findFulfillmentByInstanceId(entity.getInstanceId()).orElse(null)
                : null;

        CustomerDeviceDetailResponse response = ledInstanceMapper.toCustomerDeviceDetail(
                entity,
                modelEntity,
                fulfillmentSnapshot,
                Instant.now()
        );

        log.info("Loaded customer LED device detail [customerId={}] [deviceId={}] [modelId={}] [status={}] [warrantyStatus={}]",
                customerId,
                response.deviceId(),
                response.model() != null ? response.model().modelId() : null,
                response.status(),
                response.warrantyStatus());

        return response;
    }
}
