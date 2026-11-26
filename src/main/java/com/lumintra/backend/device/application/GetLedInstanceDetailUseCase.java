package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.LedInstanceDetailResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Use case for inspecting a single physical LED hardware instance, its parent model specs,
 * derived warranty status, fulfillment linkage, and re-printable factory QR payload.
 */
@Service
@RequiredArgsConstructor
public class GetLedInstanceDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetLedInstanceDetailUseCase.class);

    private final LedInstanceRepository ledInstanceRepository;
    private final LedModelRepository ledModelRepository;
    private final CustomerLookupService customerLookupService;
    private final OrderFulfillmentLookupService orderFulfillmentLookupService;
    private final LedInstanceMapper ledInstanceMapper;

    @Transactional(readOnly = true)
    public LedInstanceDetailResponse execute(Long instanceId) {
        LedInstanceEntity entity = ledInstanceRepository.findById(instanceId)
                .orElseThrow(() -> {
                    log.warn("LED instance detail not found [instanceId={}]", instanceId);
                    return new LedInstanceNotFoundException("LED instance not found.");
                });

        LedModelEntity modelEntity = entity.getModelId() != null
                ? ledModelRepository.findById(entity.getModelId()).orElse(null)
                : null;

        CustomerProfileSnapshot ownerProfile = entity.getOwnerCustomerId() != null
                ? customerLookupService.findProfileByCustomerId(entity.getOwnerCustomerId()).orElse(null)
                : null;

        InstanceFulfillmentSnapshot fulfillmentSnapshot = resolveFulfillment(entity.getInstanceId());

        LedInstanceDetailResponse response = ledInstanceMapper.toDetail(
                entity,
                modelEntity,
                ownerProfile,
                fulfillmentSnapshot,
                Instant.now()
        );

        log.info("Loaded LED hardware instance detail [instanceId={}] [modelId={}] [status={}] [warrantyStatus={}]",
                response.instanceId(),
                response.model() != null ? response.model().modelId() : null,
                response.status(),
                response.warrantyStatus());

        return response;
    }

    private InstanceFulfillmentSnapshot resolveFulfillment(Long instanceId) {
        if (instanceId == null) {
            return null;
        }
        Map<Long, InstanceFulfillmentSnapshot> fulfillmentsByInstanceId =
                orderFulfillmentLookupService.findFulfillmentsByInstanceIds(List.of(instanceId));
        return fulfillmentsByInstanceId != null ? fulfillmentsByInstanceId.get(instanceId) : null;
    }
}
