package com.lumintra.backend.device.application;

import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeprecateLedModelUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeprecateLedModelUseCase.class);

    private final LedModelRepository ledModelRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Soft-deprecates an obsolete LED hardware model (status = DEPRECATED).
     * Never hard-deletes the database record to preserve hardware fleet integrity (BR-FL5-12).
     * If the model is linked to a PUBLISHED store product, transitions that product to ARCHIVED
     * and publishes a cache invalidation event.
     *
     * @param id the ID of the LED model to deprecate
     */
    @Transactional
    public void execute(Long id) {
        LedModelEntity model = ledModelRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("LED model deprecation failed: model not found [modelId={}]", id);
                    return new LedModelNotFoundException(id);
                });

        model.deprecate();
        ledModelRepository.save(model);
        log.info("LED model deprecated successfully [modelId={}] [modelName={}]", id, model.getModelName());

        Long productId = model.getProductId();
        if (productId != null) {
            productRepository.findById(productId).ifPresent(product -> {
                if (product.getStatus() == ProductStatus.PUBLISHED) {
                    product.setStatus(ProductStatus.ARCHIVED);
                    productRepository.save(product);
                    log.info("Linked store product transitioned to ARCHIVED [productId={}] [modelId={}]", productId, id);

                    eventPublisher.publishEvent(new StoreProductUpdatedEvent(productId));
                }
            });
        }
    }
}
