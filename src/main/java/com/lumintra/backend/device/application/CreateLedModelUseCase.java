package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.CreateLedModelRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.DuplicateLedModelNameException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreateLedModelUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateLedModelUseCase.class);

    private final LedModelRepository ledModelRepository;
    private final LedModelMapper ledModelMapper;

    @Transactional
    public LedModelListResponse.LedModelItem execute(CreateLedModelRequest request) {
        String trimmedName = request.modelName().strip();
        if (ledModelRepository.existsByModelNameIgnoreCase(trimmedName)) {
            log.warn("Failed to create LED model: modelName already exists [modelName={}]", trimmedName);
            throw new DuplicateLedModelNameException(trimmedName);
        }

        Map<String, Object> specMap = new LinkedHashMap<>();
        specMap.put("widthPx", request.spec().widthPx());
        specMap.put("heightPx", request.spec().heightPx());

        List<Map<String, String>> attributesList = request.spec().attributes().stream()
                .map(attr -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("label", attr.label());
                    entry.put("value", attr.value());
                    return entry;
                })
                .toList();
        specMap.put("attributes", attributesList);

        LedModelEntity entity = LedModelEntity.createStandalone(
                trimmedName,
                request.spec().widthPx(),
                request.spec().heightPx(),
                specMap,
                request.stockQuantity()
        );

        LedModelEntity saved = ledModelRepository.save(entity);
        log.info("LED model created successfully [modelId={}] [modelName={}] [status={}] [stock={}]",
                saved.getModelId(), saved.getModelName(), saved.getStatus(), saved.getStockQuantity());

        return ledModelMapper.toItem(saved);
    }
}
