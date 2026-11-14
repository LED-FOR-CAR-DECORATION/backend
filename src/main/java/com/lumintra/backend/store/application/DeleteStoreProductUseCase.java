package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteStoreProductUseCase {
    private final ProductRepository products;
    private final ApplicationEventPublisher events;

    @Transactional
    public void execute(long id) {
        var product =
                products.findForManagementUpdate(id)
                        .filter(candidate -> candidate.getStatus() != ProductStatus.DELETED)
                        .orElseThrow(ProductNotFoundException::new);
        product.setStatus(ProductStatus.DELETED);
        events.publishEvent(new StoreProductUpdatedEvent(id));
    }
}
