package com.lumintra.backend.store.application;

import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.api.response.ProductListResponse;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheNames;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListProductsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListProductsUseCase.class);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Cacheable(value = StoreCacheNames.PRODUCTS, key = "#request.toCacheKey()", unless = "#result == null")
    @Transactional(readOnly = true)
    public ProductListResponse execute(ProductFilterRequest request) {
        Pageable pageable = PageRequest.of(request.page(), request.size());

        Page<ProductEntity> productPage = productRepository.findAll(
                ProductSpecifications.withFilter(request),
                pageable
        );

        ProductListResponse response = productMapper.toProductListResponse(productPage);

        log.info("Catalog browsing completed [page={}] [size={}] [totalElements={}]",
                response.meta().getPage(), response.meta().getSize(), response.meta().getTotalElements());

        return response;
    }
}
