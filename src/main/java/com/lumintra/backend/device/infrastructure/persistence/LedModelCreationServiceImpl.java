package com.lumintra.backend.device.infrastructure.persistence;

import com.lumintra.backend.device.domain.service.LedModelCreationService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;

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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createForProduct(
            long productId, String modelName, Map<String, Object> spec, long stockQuantity) {
        entityManager.persist(LedModelEntity.createForProduct(productId, modelName, spec, stockQuantity));
    }
}
