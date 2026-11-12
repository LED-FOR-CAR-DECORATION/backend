package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.store.api.request.UpdateStoreProductStatusRequest;
import com.lumintra.backend.store.api.response.UpdatedStoreProductStatusResponse;
import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateStoreProductStatusUseCase {
    private final ProductRepository products;
    private final ApplicationEventPublisher events;

    @Transactional
    public UpdatedStoreProductStatusResponse execute(
            long id, UpdateStoreProductStatusRequest request) {
        var product =
                products.findForManagementUpdate(id)
                        .filter(candidate -> candidate.getStatus() != ProductStatus.DELETED)
                        .orElseThrow(ProductNotFoundException::new);
        var status = request.status().toProductStatus();
        if (product.getStatus() != status) {
            product.setStatus(status);
            events.publishEvent(new StoreProductUpdatedEvent(id));
        }
        return new UpdatedStoreProductStatusResponse(id, status);
    }
}
