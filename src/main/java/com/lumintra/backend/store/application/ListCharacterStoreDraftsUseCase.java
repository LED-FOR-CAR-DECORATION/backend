package com.lumintra.backend.store.application;

import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListCharacterStoreDraftsUseCase {
    private final ProductRepository products;

    @Transactional(readOnly = true)
    public List<Draft> execute() {
        return products.findAllByProductTypeAndStatusOrderByProductIdDesc(ProductType.CHARACTER, ProductStatus.UNPUBLISHED)
                .stream()
                .map(product -> new Draft(product.getProductId(), product.getProductName(), product.getBasePrice(),
                        product.getDescription(), product.getThumbnailUrl(), List.copyOf(product.getGalleryUrls()),
                        product.getCreatedAt(), product.getUpdatedAt()))
                .toList();
    }

    public record Draft(Long productId, String productName, BigDecimal basePrice, String description,
                        String thumbnailUrl, List<String> galleryUrls, Instant createdAt, Instant updatedAt) {}
}
