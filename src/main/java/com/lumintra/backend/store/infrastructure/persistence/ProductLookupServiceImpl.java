package com.lumintra.backend.store.infrastructure.persistence;

import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductLookupServiceImpl implements ProductLookupService {

    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ProductSnapshot> findSnapshotsByIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return productRepository.findAllById(productIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(
                        ProductSnapshot::productId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private ProductSnapshot toSnapshot(ProductEntity entity) {
        return new ProductSnapshot(
                entity.getProductId(),
                entity.getProductName(),
                entity.getProductType(),
                entity.getThumbnailUrl(),
                entity.getBasePrice(),
                entity.getStatus()
        );
    }
}
