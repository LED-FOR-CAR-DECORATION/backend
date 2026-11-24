package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelCreationService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException.Reason;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class LedModelCreationServiceImpl implements LedModelCreationService {
    private final EntityManager entityManager;
    private final LedModelRepository ledModelRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long createForProduct(
            long productId, String modelName, Map<String, Object> spec, long stockQuantity) {
        LedModelEntity entity = LedModelEntity.createLinked(productId, modelName, spec, stockQuantity);
        entityManager.persist(entity);
        entityManager.flush();
        return entity.getModelId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LedModelSnapshot linkExistingToProduct(long modelId, long productId) {
        LedModelEntity entity = ledModelRepository.findByIdForUpdate(modelId)
                .orElseThrow(() -> new StoreProductReferenceException(
                        Reason.LED_MODEL_NOT_FOUND, "LED model not found."));
        if (entity.getProductId() != null) {
            throw new StoreProductReferenceException(
                    Reason.LED_MODEL_ALREADY_LINKED, "LED model already belongs to a product.");
        }
        entity.setProductId(productId);
        return new LedModelSnapshot(
                entity.getModelId(),
                entity.getModelName(),
                entity.getSpec(),
                entity.getStockQuantity() != null ? entity.getStockQuantity() : 0L
        );
    }
}
