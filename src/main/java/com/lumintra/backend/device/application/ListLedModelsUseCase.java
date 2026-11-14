package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.LedModelFilterRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelSpecifications;
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

import java.util.List;

/**
 * Use case for listing and filtering LED hardware models for internal catalog management.
 */
@Service
@RequiredArgsConstructor
public class ListLedModelsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListLedModelsUseCase.class);

    private final LedModelRepository ledModelRepository;
    private final LedModelMapper ledModelMapper;

    @Transactional(readOnly = true)
    public LedModelListResponse execute(LedModelFilterRequest request) {
        LedModelFilterRequest effective = request != null
                ? request
                : new LedModelFilterRequest(null, null, null, null, null, null, null, null);

        Pageable pageable = PageRequest.of(
                effective.page(),
                effective.size(),
                Sort.by(Sort.Direction.DESC, "modelId"));

        Page<LedModelEntity> page = ledModelRepository.findAll(
                LedModelSpecifications.withFilter(effective),
                pageable);

        List<LedModelListResponse.LedModelItem> items = page.getContent().stream()
                .map(ledModelMapper::toItem)
                .toList();

        log.info("Listed LED hardware models [page={}] [size={}] [totalElements={}] [status={}] [unlinkedOnly={}] [lowStockOnly={}]",
                effective.page(),
                effective.size(),
                page.getTotalElements(),
                effective.status(),
                Boolean.TRUE.equals(effective.unlinkedToProductOnly()),
                Boolean.TRUE.equals(effective.lowStockOnly()));

        return new LedModelListResponse(items, PaginationMeta.from(page));
    }
}
