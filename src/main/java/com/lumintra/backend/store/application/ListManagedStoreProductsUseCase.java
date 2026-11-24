package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.api.response.ManagedStoreProductResponse;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/** Lists store products across all lifecycle statuses for Manager/Admin management views. */
@Service
@RequiredArgsConstructor
public class ListManagedStoreProductsUseCase {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public ManagedStoreProductResponse execute(
            ProductType productType,
            ProductStatus status,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "productId"));
        Page<ProductEntity> resultPage = productRepository.findAll(
                ProductSpecifications.withManagementFilter(productType, status),
                pageable
        );

        List<ManagedStoreProductResponse.Item> items = resultPage.getContent().stream()
                .map(entity -> ManagedStoreProductResponse.Item.builder()
                        .productId(entity.getProductId())
                        .productName(entity.getProductName())
                        .description(entity.getDescription())
                        .productType(entity.getProductType())
                        .status(entity.getStatus())
                        .basePrice(entity.getBasePrice())
                        .thumbnailUrl(entity.getThumbnailUrl())
                        .galleryUrls(entity.getGalleryUrls() != null
                                ? List.copyOf(entity.getGalleryUrls())
                                : Collections.emptyList())
                        .tags(productMapper.toTagItemList(entity.getTags()))
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build())
                .toList();

        return ManagedStoreProductResponse.builder()
                .items(items)
                .meta(PaginationMeta.from(resultPage))
                .build();
    }
}
