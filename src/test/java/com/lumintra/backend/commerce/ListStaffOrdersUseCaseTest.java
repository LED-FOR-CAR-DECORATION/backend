package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.StaffOrderFilterRequest;
import com.lumintra.backend.commerce.api.response.StaffOrderListResponse;
import com.lumintra.backend.commerce.application.ListStaffOrdersUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliverySummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemSummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentSummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListStaffOrdersUseCaseTest {

    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private CustomerLookupService customerLookupService;

    private ListStaffOrdersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListStaffOrdersUseCase(
                internalEmployeeAccessService,
                orderRepository,
                orderItemRepository,
                orderDeliveryRepository,
                paymentTransactionRepository,
                productLookupService,
                customerLookupService,
                new OrderMapper()
        );
    }

    @Test
    @DisplayName("COM-10 UTCID01 [N]: Active staff retrieves sorted physical queue with batch-loaded customer, delivery, payment, and product details")
    void execute_withOrders_returnsSortedStaffSummariesWithBatchLookups() {
        Instant createdAt = Instant.parse("2026-11-10T16:45:00Z");
        Instant updatedAt = Instant.parse("2026-11-10T16:50:00Z");

        OrderEntity order = OrderEntity.of(
                102L,
                15L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.MIXED,
                OrderStatus.PROCESSING,
                createdAt.plusSeconds(900)
        );
        order.setCreatedAt(createdAt);
        order.setUpdatedAt(updatedAt);

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId")));
        Page<OrderEntity> page = new PageImpl<>(List.of(order), pageable, 1);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(orderItemRepository.findSummaryRowsByOrderIds(List.of(102L)))
                .thenReturn(List.of(
                        new OrderItemSummaryRow(102L, 201L),
                        new OrderItemSummaryRow(102L, 202L)
                ));
        when(productLookupService.findSnapshotsByIds(Set.of(201L)))
                .thenReturn(Map.of(
                        201L, new ProductSnapshot(
                                201L,
                                "Lumintra LED Matrix v1",
                                ProductType.LED,
                                "https://cdn.lumintra.com/products/led-thumb.png",
                                new BigDecimal("200000.00"),
                                ProductStatus.PUBLISHED
                        )
                ));
        when(orderDeliveryRepository.findSummaryRowsByOrderIds(List.of(102L)))
                .thenReturn(List.of(new OrderDeliverySummaryRow(102L, DeliveryStatus.PENDING, "Dang Phuoc Nguyen", "0912345678")));
        when(paymentTransactionRepository.findSummaryRowsByOrderIds(List.of(102L)))
                .thenReturn(List.of(
                        new PaymentSummaryRow(102L, PaymentStatus.SUCCESS),
                        new PaymentSummaryRow(102L, PaymentStatus.PENDING)
                ));
        when(customerLookupService.findProfilesByCustomerIds(Set.of(15L)))
                .thenReturn(Map.of(15L, new CustomerProfileSnapshot(15L, "Nguyen Van A", "0909111222", "customer@example.com")));

        StaffOrderListResponse response = useCase.execute("7", new StaffOrderFilterRequest());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("orderId").getDirection()).isEqualTo(Sort.Direction.DESC);

        assertThat(response.items()).hasSize(1);
        StaffOrderListResponse.StaffOrderSummaryItem item = response.items().getFirst();
        assertThat(item.orderId()).isEqualTo(102L);
        assertThat(item.orderType()).isEqualTo(OrderType.MIXED);
        assertThat(item.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(item.paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(item.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(item.customerId()).isEqualTo(15L);
        assertThat(item.customerFullName()).isEqualTo("Nguyen Van A");
        assertThat(item.customerEmail()).isEqualTo("customer@example.com");
        assertThat(item.recipientName()).isEqualTo("Dang Phuoc Nguyen");
        assertThat(item.recipientPhone()).isEqualTo("0912345678");
        assertThat(item.totalAmount()).isEqualByComparingTo("250000.00");
        assertThat(item.totalItems()).isEqualTo(2);
        assertThat(item.primaryProductName()).isEqualTo("Lumintra LED Matrix v1");
        assertThat(item.primaryProductThumbnail()).isEqualTo("https://cdn.lumintra.com/products/led-thumb.png");
        assertThat(item.createdAt()).isEqualTo(createdAt);
        assertThat(item.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("COM-10 UTCID02 [B]: Empty page short-circuits all batch lookups")
    void execute_emptyPage_skipsBatchQueries() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId")));
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

        StaffOrderListResponse response = useCase.execute("7", new StaffOrderFilterRequest());

        assertThat(response.items()).isEmpty();
        assertThat(response.meta().getTotalElements()).isZero();
        verifyNoInteractions(
                orderItemRepository,
                orderDeliveryRepository,
                paymentTransactionRepository,
                productLookupService,
                customerLookupService
        );
    }

    @Test
    @DisplayName("COM-10 UTCID03 [B]: Missing product, customer, or payment rows degrade cleanly without null pointers")
    void execute_missingEnrichmentData_usesSafeFallbacks() {
        Instant createdAt = Instant.parse("2026-11-10T10:00:00Z");
        OrderEntity order = OrderEntity.of(
                109L,
                88L,
                null,
                new BigDecimal("490000.00"),
                BigDecimal.ZERO,
                new BigDecimal("490000.00"),
                OrderType.PHYSICAL,
                OrderStatus.PARTIALLY_FULFILLED,
                createdAt.plusSeconds(900)
        );
        order.setCreatedAt(createdAt);

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId")));
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderItemRepository.findSummaryRowsByOrderIds(List.of(109L)))
                .thenReturn(List.of(new OrderItemSummaryRow(109L, 999L)));
        when(productLookupService.findSnapshotsByIds(Set.of(999L))).thenReturn(Collections.emptyMap());
        when(orderDeliveryRepository.findSummaryRowsByOrderIds(List.of(109L))).thenReturn(Collections.emptyList());
        when(paymentTransactionRepository.findSummaryRowsByOrderIds(List.of(109L))).thenReturn(Collections.emptyList());
        when(customerLookupService.findProfilesByCustomerIds(Set.of(88L))).thenReturn(Collections.emptyMap());

        StaffOrderListResponse response = useCase.execute("7", null);

        assertThat(response.items()).hasSize(1);
        StaffOrderListResponse.StaffOrderSummaryItem item = response.items().getFirst();
        assertThat(item.primaryProductName()).isEqualTo("Unavailable Product");
        assertThat(item.primaryProductThumbnail()).isNull();
        assertThat(item.customerFullName()).isNull();
        assertThat(item.customerEmail()).isNull();
        assertThat(item.deliveryStatus()).isNull();
        assertThat(item.recipientName()).isNull();
        assertThat(item.paymentStatus()).isNull();
    }

    @Test
    @DisplayName("COM-10 UTCID04 [A]: Stale or unauthorized employee fails before querying orders")
    void execute_unauthorizedEmployee_failsBeforeOrderQuery() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES))
                .thenThrow(new AccessDeniedException("Authorized internal role is required."));

        assertThatThrownBy(() -> useCase.execute("7", new StaffOrderFilterRequest()))
                .isInstanceOf(AccessDeniedException.class);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("bad", ALLOWED_ROLES))
                .thenThrow(new InvalidCredentialsException("Invalid authenticated account."));

        assertThatThrownBy(() -> useCase.execute("bad", new StaffOrderFilterRequest()))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(orderRepository, orderItemRepository, orderDeliveryRepository,
                paymentTransactionRepository, productLookupService, customerLookupService);
    }

    @Test
    @DisplayName("COM-10 UTCID05 [A]: Database failure propagates cleanly instead of returning empty results")
    void execute_repositoryFailure_propagatesException() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("7", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenThrow(new IllegalStateException("PostgreSQL timeout"));

        assertThatThrownBy(() -> useCase.execute("7", new StaffOrderFilterRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL timeout");
    }
}
