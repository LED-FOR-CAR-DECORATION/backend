package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.UpdateLedModelRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.DuplicateLedModelNameException;
import com.lumintra.backend.shared.exception.device.LedModelDimensionsLockedException;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UpdateLedModelUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateLedModelUseCase.class);

    private final LedModelRepository ledModelRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final LedModelMapper ledModelMapper;

    @Transactional
    public LedModelListResponse.LedModelItem execute(Long id, UpdateLedModelRequest request) {
        LedModelEntity entity = ledModelRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("LED model update failed: model not found [modelId={}]", id);
                    return new LedModelNotFoundException(id);
                });

        String trimmedName = request.modelName().strip();
        if (ledModelRepository.existsByModelNameIgnoreCaseAndModelIdNot(trimmedName, id)) {
            log.warn("LED model update failed: duplicate modelName [modelId={}] [modelName={}]", id, trimmedName);
            throw new DuplicateLedModelNameException(trimmedName);
        }

        Integer reqWidth = request.spec().widthPx();
        Integer reqHeight = request.spec().heightPx();

        boolean dimensionsChanged = !Objects.equals(reqWidth, entity.getWidthPx())
                || !Objects.equals(reqHeight, entity.getHeightPx());

        if (dimensionsChanged && ledInstanceRepository.existsByModelId(id)) {
            log.warn("LED model update rejected: dimensions locked [modelId={}] [existingWidth={}] [existingHeight={}] [reqWidth={}] [reqHeight={}]",
                    id, entity.getWidthPx(), entity.getHeightPx(), reqWidth, reqHeight);
            throw new LedModelDimensionsLockedException(id);
        }

        Map<String, Object> specMap = new LinkedHashMap<>();
        specMap.put("widthPx", reqWidth);
        specMap.put("heightPx", reqHeight);

        List<Map<String, String>> attributesList = request.spec().attributes().stream()
                .map(attr -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("label", attr.label());
                    entry.put("value", attr.value());
                    return entry;
                })
                .toList();
        specMap.put("attributes", attributesList);

        entity.updateDetails(trimmedName, reqWidth, reqHeight, specMap, request.stockQuantity());

        LedModelEntity saved = ledModelRepository.save(entity);
        log.info("LED model updated successfully [modelId={}] [modelName={}] [status={}] [stock={}]",
                saved.getModelId(), saved.getModelName(), saved.getStatus(), saved.getStockQuantity());

        return ledModelMapper.toItem(saved);
    }
}
