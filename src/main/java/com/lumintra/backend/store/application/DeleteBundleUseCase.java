package com.lumintra.backend.store.application;

import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.shared.exception.store.BundleNotFoundException;
import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteBundleUseCase {
    private final BundleRepository bundles;
    private final ProductRepository products;
    private final OrderItemRepository orderItems;
    private final ApplicationEventPublisher events;

    @Transactional
    public void execute(long id) {
        var bundle = bundles.findByBundleId(id).orElseThrow(BundleNotFoundException::new);
        var product =
                products.findForManagementUpdate(bundle.getProductId())
                        .filter(candidate -> candidate.getProductType() == ProductType.BUNDLE)
                        .orElseThrow(BundleNotFoundException::new);

        var status =
                product.getStatus() == ProductStatus.DELETED
                                || ProductStatus.DELETED.name().equals(bundle.getStatus())
                                || !orderItems.existsByProductId(product.getProductId())
                        ? ProductStatus.DELETED
                        : ProductStatus.ARCHIVED;
        bundle.setStatus(status.name());
        product.setStatus(status);
        events.publishEvent(new StoreProductUpdatedEvent(product.getProductId()));
    }
}
