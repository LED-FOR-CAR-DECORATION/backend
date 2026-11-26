package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.LedInstanceFilterRequest;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceSpecifications;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.shared.response.PaginationMeta;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use case for listing and tracking individual physical LED instances with cross-module enrichment.
 */
@Service
@RequiredArgsConstructor
public class ListLedInstancesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListLedInstancesUseCase.class);

    private final LedInstanceRepository ledInstanceRepository;
    private final LedModelLookupService ledModelLookupService;
    private final CustomerLookupService customerLookupService;
    private final OrderFulfillmentLookupService orderFulfillmentLookupService;
    private final LedInstanceMapper ledInstanceMapper;

    @Transactional(readOnly = true)
    public LedInstanceListResponse execute(LedInstanceFilterRequest request) {
        LedInstanceFilterRequest effective = request != null
                ? request
                : new LedInstanceFilterRequest(null, null, null, null, null);

        Pageable pageable = PageRequest.of(
                effective.page(),
                effective.size(),
                Sort.by(Sort.Direction.DESC, "instanceId"));

        Page<LedInstanceEntity> page = ledInstanceRepository.findAll(
                LedInstanceSpecifications.withFilter(effective),
                pageable);

        List<LedInstanceEntity> content = page.getContent();
        if (content.isEmpty()) {
            log.info("Listed LED hardware instances [page={}] [size={}] [totalElements={}] [modelId={}] [status={}]",
                    effective.page(), effective.size(), page.getTotalElements(), effective.modelId(), effective.status());
            return new LedInstanceListResponse(List.of(), PaginationMeta.from(page));
        }

        Set<Long> modelIds = content.stream()
                .map(LedInstanceEntity::getModelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> ownerIds = content.stream()
                .map(LedInstanceEntity::getOwnerCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> instanceIds = content.stream()
                .map(LedInstanceEntity::getInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, LedModelSnapshot> modelsById = modelIds.isEmpty()
                ? Collections.emptyMap()
                : ledModelLookupService.findByModelIds(modelIds);

        Map<Long, CustomerProfileSnapshot> ownersById = ownerIds.isEmpty()
                ? Collections.emptyMap()
                : customerLookupService.findProfilesByCustomerIds(ownerIds);

        Map<Long, InstanceFulfillmentSnapshot> fulfillmentsByInstanceId = instanceIds.isEmpty()
                ? Collections.emptyMap()
                : orderFulfillmentLookupService.findFulfillmentsByInstanceIds(instanceIds);

        List<LedInstanceListResponse.LedInstanceItem> items = content.stream()
                .map(entity -> ledInstanceMapper.toItem(entity, modelsById, ownersById, fulfillmentsByInstanceId))
                .toList();

        log.info("Listed LED hardware instances [page={}] [size={}] [totalElements={}] [modelId={}] [status={}]",
                effective.page(),
                effective.size(),
                page.getTotalElements(),
                effective.modelId(),
                effective.status());

        return new LedInstanceListResponse(items, PaginationMeta.from(page));
    }
}
