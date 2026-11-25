package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.StockInLedInstancesRequest;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.api.response.StockInLedInstancesResponse;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.DuplicateLedInstanceException;
import com.lumintra.backend.shared.exception.device.LedModelDeprecatedException;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StockInLedInstancesUseCase {

    private static final Logger log = LoggerFactory.getLogger(StockInLedInstancesUseCase.class);

    private final LedModelRepository ledModelRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final LedInstanceMapper ledInstanceMapper;

    @Transactional
    public StockInLedInstancesResponse execute(StockInLedInstancesRequest request) {
        LedModelEntity model = ledModelRepository.findById(request.modelId())
                .orElseThrow(() -> new LedModelNotFoundException(request.modelId()));

        if (model.getStatus() == LedModelStatus.DEPRECATED) {
            throw new LedModelDeprecatedException(model.getModelId());
        }

        Set<String> serialNumbers = new LinkedHashSet<>();
        Set<String> normalizedSerials = new LinkedHashSet<>();
        Set<String> macAddresses = new LinkedHashSet<>();

        for (StockInLedInstancesRequest.StockInLedInstanceItem item : request.items()) {
            String serial = item.serialNumber();
            String serialKey = serial.toUpperCase(Locale.ROOT);
            if (!normalizedSerials.add(serialKey)) {
                throw new DuplicateLedInstanceException(
                        "Duplicate serial number in batch: " + serial);
            }
            serialNumbers.add(serial);

            String mac = item.macAddress().toUpperCase(Locale.ROOT);
            if (!macAddresses.add(mac)) {
                throw new DuplicateLedInstanceException(
                        "Duplicate MAC address in batch: " + mac);
            }
        }

        List<LedInstanceEntity> existingBySerial = ledInstanceRepository.findBySerialNumberIn(serialNumbers);
        if (!existingBySerial.isEmpty()) {
            throw new DuplicateLedInstanceException(
                    "Serial number already exists: " + existingBySerial.get(0).getSerialNumber());
        }

        List<LedInstanceEntity> existingByMac = ledInstanceRepository.findByMacAddressIn(macAddresses);
        if (!existingByMac.isEmpty()) {
            throw new DuplicateLedInstanceException(
                    "MAC address already exists: " + existingByMac.get(0).getMacAddress());
        }

        Instant now = Instant.now();
        List<LedInstanceEntity> toSave = new ArrayList<>(request.items().size());
        for (StockInLedInstancesRequest.StockInLedInstanceItem item : request.items()) {
            Instant manufacturedAt = item.manufacturedAt() != null ? item.manufacturedAt() : now;

            toSave.add(LedInstanceEntity.createInStock(
                    model.getModelId(),
                    item.serialNumber(),
                    item.macAddress(),
                    manufacturedAt
            ));
        }

        List<LedInstanceEntity> savedInstances = ledInstanceRepository.saveAll(toSave);

        long currentStock = model.getStockQuantity() != null ? model.getStockQuantity() : 0L;
        long updatedStock = currentStock + savedInstances.size();
        model.setStockQuantity(updatedStock);
        if (updatedStock > 0L) {
            model.setStatus(LedModelStatus.ACTIVE);
        }
        LedModelEntity savedModel = ledModelRepository.save(model);

        Map<Long, LedModelSnapshot> modelMap = Map.of(
                savedModel.getModelId(),
                new LedModelSnapshot(
                        savedModel.getModelId(),
                        savedModel.getModelName(),
                        savedModel.getSpec(),
                        savedModel.getStockQuantity()
                )
        );

        List<LedInstanceListResponse.LedInstanceItem> mappedItems = savedInstances.stream()
                .map(entity -> ledInstanceMapper.toItem(
                        entity,
                        modelMap,
                        Collections.emptyMap(),
                        Collections.emptyMap()))
                .toList();

        log.info("Stocked in LED instances [modelId={}] [importedCount={}] [newStockQuantity={}]",
                savedModel.getModelId(), savedInstances.size(), savedModel.getStockQuantity());

        return StockInLedInstancesResponse.builder()
                .modelId(savedModel.getModelId())
                .modelName(savedModel.getModelName())
                .importedCount(savedInstances.size())
                .stockQuantity(savedModel.getStockQuantity())
                .modelStatus(savedModel.getStatus())
                .items(mappedItems)
                .build();
    }
}
