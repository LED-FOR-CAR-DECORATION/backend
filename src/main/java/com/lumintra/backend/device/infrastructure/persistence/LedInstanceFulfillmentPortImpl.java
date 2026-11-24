package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.commerce.domain.model.FulfillmentDeviceSnapshot;
import com.lumintra.backend.commerce.domain.service.LedInstanceFulfillmentPort;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.commerce.OrderFulfillmentException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LedInstanceFulfillmentPortImpl implements LedInstanceFulfillmentPort {

        private static final Logger log = LoggerFactory.getLogger(LedInstanceFulfillmentPortImpl.class);

        private final LedInstanceRepository ledInstanceRepository;
        private final LedModelRepository ledModelRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findModelIdByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return ledModelRepository.findByProductId(productId).map(LedModelEntity::getModelId);
    }

    @Override
    @Transactional
    public FulfillmentDeviceSnapshot linkAndMarkInTransit(
            String serialNumber,
            Long expectedModelId) {
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new OrderFulfillmentException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "serialNumber must be provided."
            );
        }

        @Override
        @Transactional
        public void releaseToStock(Long instanceId) {
                if (instanceId == null) {
                        return;
                }
                int rows = ledInstanceRepository.updateStatusIfCurrent(
                                instanceId,
                                LedInstanceStatus.IN_TRANSIT,
                                LedInstanceStatus.IN_STOCK,
                                Instant.now());
                log.info("Released LED instance back to IN_STOCK [instanceId={}] [updatedRows={}]", instanceId, rows);
        }
}
