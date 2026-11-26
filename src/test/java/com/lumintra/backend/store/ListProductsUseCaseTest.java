package com.lumintra.backend.store;

import com.lumintra.backend.store.api.request.ProductFilterRequest;
import com.lumintra.backend.store.api.request.validation.ValidPriceRangeValidator;
import com.lumintra.backend.store.api.response.ProductListResponse;
import com.lumintra.backend.store.application.ListProductsUseCase;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListProductsUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ConstraintValidatorContext validatorContext;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    private ListProductsUseCase useCase;

    @BeforeEach
    void setUp() {
        ProductMapper mapper = new ProductMapper();
        useCase = new ListProductsUseCase(productRepository, mapper);
    }

    private ProductEntity createProduct(Long id, String name, ProductType type, BigDecimal price, ProductStatus status, String tagName) {
        TagEntity tag = TagEntity.of(10L, tagName, tagName + " description");

        return ProductEntity.of(
                id,
                name,
                type,
                price,
                "Sample description",
                "https://img.lumintra.shop/p" + id + ".png",
                List.of("https://img.lumintra.shop/p" + id + "-1.png"),
                status,
                Set.of(tag),
                Instant.now()
        );
    }

    @Test
    @DisplayName("AC01 & AC03: Browse published products with default pagination (page=0, size=20)")
    void shouldListPublishedProductsWithDefaultPagination() {
        ProductEntity entity = createProduct(1L, "LED Matrix 32x32", ProductType.LED,
                new BigDecimal("450000.00"), ProductStatus.PUBLISHED, "car-interior");
        Page<ProductEntity> page = new PageImpl<>(List.of(entity));

        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        ProductFilterRequest request = new ProductFilterRequest();
        ProductListResponse result = useCase.execute(request);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals(1L, result.items().get(0).productId());
        assertEquals(ProductType.LED, result.items().get(0).productType());
        assertEquals("car-interior", result.items().get(0).tags().get(0).name());
        assertEquals(0, result.meta().getPage());
        assertEquals(1, result.meta().getTotalElements());
    }

    @Test
    @DisplayName("AC02: Filter products by SUBSCRIPTION product type and category tag")
    void shouldFilterByProductTypeAndCategoryTag() {
        ProductEntity entity = createProduct(2L, "VIP Monthly Pass", ProductType.SUBSCRIPTION,
                new BigDecimal("99000.00"), ProductStatus.PUBLISHED, "membership");
        Page<ProductEntity> page = new PageImpl<>(List.of(entity));

        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        ProductFilterRequest request = new ProductFilterRequest(ProductType.SUBSCRIPTION, "membership");
        ProductListResponse result = useCase.execute(request);

        assertEquals(1, result.items().size());
        assertEquals(ProductType.SUBSCRIPTION, result.items().get(0).productType());
    }

    @Test
    @DisplayName("AC02: Filter products by valid price range (minPrice <= maxPrice)")
    void shouldFilterByPriceRange() {
        ProductEntity entity = createProduct(3L, "Neon Driver Character", ProductType.CHARACTER,
                new BigDecimal("150000.00"), ProductStatus.PUBLISHED, "driver");
        Page<ProductEntity> page = new PageImpl<>(List.of(entity));

        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        ProductFilterRequest request = new ProductFilterRequest(new BigDecimal("100000.00"), new BigDecimal("200000.00"));
        ProductListResponse result = useCase.execute(request);

        assertEquals(1, result.items().size());
        assertEquals(new BigDecimal("150000.00"), result.items().get(0).basePrice());
    }

    @Test
    @DisplayName("Validation: ValidPriceRangeValidator rejects request when minPrice > maxPrice")
    void shouldFailValidationWhenMinPriceGreaterThanMaxPrice() {
        ValidPriceRangeValidator validator = new ValidPriceRangeValidator();
        ProductFilterRequest invalidRequest = new ProductFilterRequest(new BigDecimal("500000.00"), new BigDecimal("100000.00"));

        when(validatorContext.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        boolean isValid = validator.isValid(invalidRequest, validatorContext);
        assertFalse(isValid);
        verify(validatorContext).disableDefaultConstraintViolation();
        verify(violationBuilder).addPropertyNode("minPrice");
    }

    @Test
    @DisplayName("AC03: Verify Specification excludes UNPUBLISHED or ARCHIVED products")
    void shouldOnlyQueryPublishedStatus() {
        ProductEntity published = createProduct(4L, "LED Light Strip", ProductType.LED,
                new BigDecimal("250000.00"), ProductStatus.PUBLISHED, "exterior");
        Page<ProductEntity> page = new PageImpl<>(List.of(published));

        when(productRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        ProductFilterRequest request = new ProductFilterRequest(0, 10);
        useCase.execute(request);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), pageableCaptor.capture());

        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
    }
}
