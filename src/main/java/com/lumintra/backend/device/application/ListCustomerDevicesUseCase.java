package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.CustomerDeviceFilterRequest;
import com.lumintra.backend.device.api.response.CustomerDeviceListResponse;
import com.lumintra.backend.device.api.response.CustomerDeviceListResponse.CustomerDeviceItem;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.response.PaginationMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListCustomerDevicesUseCase {

    private final LedInstanceRepository ledInstanceRepository;
    private final LedModelRepository ledModelRepository;
    private final LedInstanceMapper ledInstanceMapper;

    @Transactional(readOnly = true)
    public CustomerDeviceListResponse execute(Long customerId, CustomerDeviceFilterRequest request) {
        if (customerId == null) {
            throw new InsufficientAuthenticationException("Authenticated customer identity is required.");
        }

        CustomerDeviceFilterRequest effective = request != null
                ? request
                : new CustomerDeviceFilterRequest(0, 20);

        Pageable pageable = PageRequest.of(effective.page(), effective.size());
        Page<LedInstanceEntity> page = ledInstanceRepository.findAllByOwnerCustomerIdOrderByBoundAtDesc(customerId, pageable);

        List<LedInstanceEntity> content = page.getContent();
        if (content.isEmpty()) {
            return CustomerDeviceListResponse.builder()
                    .items(List.of())
                    .meta(PaginationMeta.from(page))
                    .build();
        }

        Set<Long> modelIds = content.stream()
                .map(LedInstanceEntity::getModelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, LedModelEntity> modelsById = modelIds.isEmpty()
                ? Map.of()
                : ledModelRepository.findAllById(modelIds).stream()
                        .collect(Collectors.toMap(LedModelEntity::getModelId, Function.identity()));

        Instant now = Instant.now();
        List<CustomerDeviceItem> items = content.stream()
                .map(instance -> ledInstanceMapper.toCustomerDeviceItem(
                        instance,
                        modelsById.get(instance.getModelId()),
                        now
                ))
                .toList();

        return CustomerDeviceListResponse.builder()
                .items(items)
                .meta(PaginationMeta.from(page))
                .build();
    }
}
