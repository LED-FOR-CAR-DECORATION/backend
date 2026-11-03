package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.ProductException;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.api.response.ProductSummaryResponse;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListProductsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListProductsUseCase.class);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public ProductPageResult execute(ProductFilterRequest request) {
        validatePriceRange(request);

        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        Pageable pageable = PageRequest.of(page, size);

        Specification<ProductEntity> spec = createSpecification(request);
        Page<ProductEntity> productPage = productRepository.findAll(spec, pageable);

        List<ProductSummaryResponse> items = productMapper.toSummaryResponseList(productPage.getContent());

        PaginationMeta meta = PaginationMeta.builder()
                .page(productPage.getNumber())
                .size(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .hasNext(productPage.hasNext())
                .hasPrevious(productPage.hasPrevious())
                .build();

        log.info("Catalog browsing completed [page={}] [size={}] [totalElements={}]",
                meta.getPage(), meta.getSize(), meta.getTotalElements());

        return new ProductPageResult(items, meta);
    }

    private void validatePriceRange(ProductFilterRequest request) {
        if (request.getMinPrice() != null && request.getMaxPrice() != null
                && request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
            log.warn("Invalid price range detected [minPrice={}] [maxPrice={}]",
                    request.getMinPrice(), request.getMaxPrice());
            throw new ProductException("INVALID_PRICE_RANGE", "Minimum price cannot be greater than maximum price");
        }
    }

    private Specification<ProductEntity> createSpecification(ProductFilterRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // AC03: Strictly hide unpublished or archived listings from public view
            predicates.add(cb.equal(root.get("status"), ProductStatus.PUBLISHED));

            // AC02: Filtering by product type
            if (request.getProductType() != null) {
                predicates.add(cb.equal(root.get("productType"), request.getProductType()));
            }

            // AC02: Filtering by category tag
            if (request.getCategoryTag() != null && !request.getCategoryTag().isBlank()) {
                Join<ProductEntity, TagEntity> tagJoin = root.join("tags", JoinType.INNER);
                predicates.add(cb.equal(cb.lower(tagJoin.get("name")), request.getCategoryTag().trim().toLowerCase()));
                if (query != null) {
                    query.distinct(true);
                }
            }

            // AC02: Filtering by price range
            if (request.getMinPrice() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("basePrice"), request.getMinPrice()));
            }
            if (request.getMaxPrice() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("basePrice"), request.getMaxPrice()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
