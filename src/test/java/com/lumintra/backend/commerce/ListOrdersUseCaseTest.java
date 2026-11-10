package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.OrderFilterRequest;
import com.lumintra.backend.commerce.api.request.validation.ValidOrderDateRangeValidator;
import com.lumintra.backend.commerce.api.response.OrderListResponse;
import com.lumintra.backend.commerce.application.ListOrdersUseCase;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemSummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderSpecifications;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListOrdersUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    private ListOrdersUseCase listOrdersUseCase;

    @BeforeEach
    void setUp() {
        listOrdersUseCase = new ListOrdersUseCase(
                orderRepository,
                orderItemRepository,
                productLookupService,
                new OrderMapper()
        );
    }

    @Test
    @DisplayName("UTCID05 [N]: AC01, AC02 & AC04 lists customer orders sorted by createdAt DESC with batch-fetched previews and conditional expiresAt")
    void execute_withOrders_returnsSortedSummariesWithBatchFetchedPreviews() {
        Long customerId = 42L;
        Instant pendingCreatedAt = Instant.parse("2026-11-10T16:45:00Z");
        Instant pendingExpiresAt = Instant.parse("2026-11-10T17:00:00Z");
        Instant completedCreatedAt = Instant.parse("2026-11-09T10:00:00Z");
        Instant completedExpiresAt = Instant.parse("2026-11-09T10:15:00Z");

        OrderEntity pendingOrder = OrderEntity.of(
                102L,
                customerId,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.MIXED,
                OrderStatus.PENDING_PAYMENT,
                pendingExpiresAt
        );
        pendingOrder.setCreatedAt(pendingCreatedAt);

        OrderEntity completedOrder = OrderEntity.of(
                101L,
                customerId,
                null,
                new BigDecimal("59000.00"),
                BigDecimal.ZERO,
                new BigDecimal("59000.00"),
                OrderType.DIGITAL,
                OrderStatus.COMPLETED,
                completedExpiresAt
        );
        completedOrder.setCreatedAt(completedCreatedAt);

        Pageable expectedPageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId"))
        );
        Page<OrderEntity> page = new PageImpl<>(List.of(pendingOrder, completedOrder), expectedPageable, 2);

        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(orderItemRepository.findSummaryRowsByOrderIds(List.of(102L, 101L)))
                .thenReturn(List.of(
                        new OrderItemSummaryRow(102L, 201L),
                        new OrderItemSummaryRow(102L, 202L),
                        new OrderItemSummaryRow(101L, 203L)
                ));
        when(productLookupService.findSnapshotsByIds(Set.of(201L, 203L)))
                .thenReturn(Map.of(
                        201L, new ProductSnapshot(201L, "Lumintra LED Matrix v1", ProductType.LED,
                                "https://cdn.lumintra.com/products/led-thumb.png", new BigDecimal("191000.00"), ProductStatus.PUBLISHED),
                        203L, new ProductSnapshot(203L, "Cyber Dragon", ProductType.CHARACTER,
                                "https://cdn.lumintra.com/products/dragon.png", new BigDecimal("59000.00"), ProductStatus.PUBLISHED)
                ));

        OrderListResponse response = listOrdersUseCase.execute(customerId, new OrderFilterRequest());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable actualPageable = pageableCaptor.getValue();
        assertThat(actualPageable.getPageNumber()).isEqualTo(0);
        assertThat(actualPageable.getPageSize()).isEqualTo(10);
        assertThat(actualPageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(actualPageable.getSort().getOrderFor("orderId").getDirection()).isEqualTo(Sort.Direction.DESC);

        verify(orderItemRepository).findSummaryRowsByOrderIds(List.of(102L, 101L));
        verify(productLookupService).findSnapshotsByIds(Set.of(201L, 203L));

        assertThat(response.items()).hasSize(2);
        OrderListResponse.OrderSummaryItem first = response.items().get(0);
        assertThat(first.orderId()).isEqualTo(102L);
        assertThat(first.orderType()).isEqualTo(OrderType.MIXED);
        assertThat(first.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(first.totalAmount()).isEqualByComparingTo("250000.00");
        assertThat(first.totalItems()).isEqualTo(2);
        assertThat(first.primaryProductName()).isEqualTo("Lumintra LED Matrix v1");
        assertThat(first.primaryProductThumbnail()).isEqualTo("https://cdn.lumintra.com/products/led-thumb.png");
        assertThat(first.expiresAt()).isEqualTo(pendingExpiresAt);
        assertThat(first.createdAt()).isEqualTo(pendingCreatedAt);

        OrderListResponse.OrderSummaryItem second = response.items().get(1);
        assertThat(second.orderId()).isEqualTo(101L);
        assertThat(second.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(second.totalItems()).isEqualTo(1);
        assertThat(second.primaryProductName()).isEqualTo("Cyber Dragon");
        assertThat(second.expiresAt()).isNull();

        assertThat(response.meta().getPage()).isZero();
        assertThat(response.meta().getSize()).isEqualTo(10);
        assertThat(response.meta().getTotalElements()).isEqualTo(2L);
        assertThat(response.meta().getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTCID06 [N]: AC01 & AC03 OrderSpecifications builds customerId, exact status, and UTC inclusive date range predicates")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void orderSpecifications_withStatusAndDateRange_buildsExpectedPredicates() {
        OrderFilterRequest filter = new OrderFilterRequest(
                OrderStatus.PROCESSING,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                0,
                10
        );

        Root<OrderEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path customerIdPath = mock(Path.class);
        Path statusPath = mock(Path.class);
        Path createdAtPath = mock(Path.class);
        Predicate p1 = mock(Predicate.class);
        Predicate p2 = mock(Predicate.class);
        Predicate p3 = mock(Predicate.class);
        Predicate p4 = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);

        when(root.get("customerId")).thenReturn(customerIdPath);
        when(root.get("status")).thenReturn(statusPath);
        when(root.get("createdAt")).thenReturn(createdAtPath);
        when(cb.equal(customerIdPath, 42L)).thenReturn(p1);
        when(cb.equal(statusPath, OrderStatus.PROCESSING)).thenReturn(p2);
        when(cb.greaterThanOrEqualTo(createdAtPath, Instant.parse("2026-11-01T00:00:00Z"))).thenReturn(p3);
        when(cb.lessThan(createdAtPath, Instant.parse("2026-12-01T00:00:00Z"))).thenReturn(p4);
        when(cb.and(any(Predicate[].class))).thenReturn(combined);

        Specification<OrderEntity> spec = OrderSpecifications.withCustomerFilter(42L, filter);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(combined);
        verify(cb).equal(customerIdPath, 42L);
        verify(cb).equal(statusPath, OrderStatus.PROCESSING);
        verify(cb).greaterThanOrEqualTo(createdAtPath, Instant.parse("2026-11-01T00:00:00Z"));
        verify(cb).lessThan(createdAtPath, Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    @DisplayName("UTCID07 [B]: Empty customer order history short-circuits batch item and product lookups")
    void execute_emptyPage_skipsBatchQueriesAndReturnsEmptyList() {
        Pageable pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId"))
        );
        Page<OrderEntity> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        OrderListResponse response = listOrdersUseCase.execute(42L, new OrderFilterRequest());

        assertThat(response.items()).isEmpty();
        assertThat(response.meta().getTotalElements()).isZero();
        assertThat(response.meta().isHasNext()).isFalse();
        verifyNoInteractions(orderItemRepository, productLookupService);
    }

    @Test
    @DisplayName("UTCID08 [B]: Missing primary product snapshot falls back cleanly to Unavailable Product and null thumbnail")
    void execute_missingProductSnapshot_usesSafeFallbackValues() {
        Instant createdAt = Instant.parse("2026-11-10T12:00:00Z");
        OrderEntity deliveredOrder = OrderEntity.of(
                105L,
                42L,
                9L,
                new BigDecimal("490000.00"),
                BigDecimal.ZERO,
                new BigDecimal("490000.00"),
                OrderType.PHYSICAL,
                OrderStatus.DELIVERED,
                createdAt.plusSeconds(900)
        );
        deliveredOrder.setCreatedAt(createdAt);

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId")));
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deliveredOrder), pageable, 1));
        when(orderItemRepository.findSummaryRowsByOrderIds(List.of(105L)))
                .thenReturn(List.of(
                        new OrderItemSummaryRow(105L, 999L),
                        new OrderItemSummaryRow(105L, 998L),
                        new OrderItemSummaryRow(105L, 997L)
                ));
        when(productLookupService.findSnapshotsByIds(Set.of(999L))).thenReturn(Collections.emptyMap());

        OrderListResponse response = listOrdersUseCase.execute(42L, null);

        assertThat(response.items()).hasSize(1);
        OrderListResponse.OrderSummaryItem item = response.items().get(0);
        assertThat(item.totalItems()).isEqualTo(3);
        assertThat(item.primaryProductName()).isEqualTo("Unavailable Product");
        assertThat(item.primaryProductThumbnail()).isNull();
        assertThat(item.expiresAt()).isNull();
    }

    @Test
    @DisplayName("UTCID09 [B]: ValidOrderDateRangeValidator accepts equal dates and rejects fromDate > toDate on property fromDate")
    void validOrderDateRangeValidator_boundaryAndInvalidDates_behaveAsExpected() {
        ValidOrderDateRangeValidator validator = new ValidOrderDateRangeValidator();
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);
        ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        when(context.getDefaultConstraintMessageTemplate()).thenReturn("fromDate must not be after toDate");
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        OrderFilterRequest sameDay = new OrderFilterRequest(
                null,
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 10),
                0,
                50
        );
        OrderFilterRequest invalidRange = new OrderFilterRequest(
                null,
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 10),
                0,
                50
        );

        assertThat(validator.isValid(sameDay, context)).isTrue();
        assertThat(validator.isValid(invalidRange, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(violationBuilder).addPropertyNode("fromDate");
    }

    @Test
    @DisplayName("UTCID10 [A]: Propagates database failure cleanly without swallowing into an empty list")
    void execute_repositoryFailure_propagatesException() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("Database connection failed"));

        assertThatThrownBy(() -> listOrdersUseCase.execute(42L, new OrderFilterRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database connection failed");

        verifyNoInteractions(orderItemRepository, productLookupService);
    }
}
