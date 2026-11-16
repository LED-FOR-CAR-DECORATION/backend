package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.LinkOrderFulfillmentDeviceRequest;
import com.lumintra.backend.commerce.api.response.LinkOrderFulfillmentDeviceResponse;
import com.lumintra.backend.commerce.application.LinkOrderFulfillmentDeviceUseCase;
import com.lumintra.backend.commerce.domain.model.FulfillmentDeviceSnapshot;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.service.LedInstanceFulfillmentPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemFulfillmentEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemFulfillmentRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderFulfillmentException;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.BundleSnapshot;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.BundleLookupService;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkOrderFulfillmentDeviceUseCaseTest {

    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    @Mock
    private InternalEmployeeAccessService internalEmployeeAccessService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderItemFulfillmentRepository orderItemFulfillmentRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private BundleLookupService bundleLookupService;

    @Mock
    private LedInstanceFulfillmentPort ledInstanceFulfillmentPort;

    private LinkOrderFulfillmentDeviceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LinkOrderFulfillmentDeviceUseCase(
                internalEmployeeAccessService,
                orderRepository,
                orderItemRepository,
                orderItemFulfillmentRepository,
                productLookupService,
                bundleLookupService,
                ledInstanceFulfillmentPort
        );
    }

    private OrderEntity sampleOrder(Long orderId, OrderStatus status, OrderType orderType) {
        return OrderEntity.of(
                orderId,
                15L,
                10L,
                new BigDecimal("500000.00"),
                BigDecimal.ZERO,
                new BigDecimal("500000.00"),
                orderType,
                status,
                Instant.parse("2026-11-20T12:00:00Z")
        );
    }

    @Test
    @DisplayName("COM-16 UTCID01 [P]: Successfully scans & links standalone LED unit to PROCESSING order")
    void execute_standaloneLed_linksSuccessfully() {
        Long orderId = 100L;
        Long orderItemId = 201L;
        OrderEntity order = sampleOrder(orderId, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        OrderItemEntity item = OrderItemEntity.create(order, 5L, 1, new BigDecimal("500000.00"));
        item.setOrderItemId(orderItemId);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(item));
        when(productLookupService.findSnapshotById(5L)).thenReturn(Optional.of(
                new ProductSnapshot(5L, "Matrix 64x64", ProductType.LED, "thumb.png", new BigDecimal("500000.00"), ProductStatus.PUBLISHED)
        ));
        when(orderItemFulfillmentRepository.countByOrderItem_OrderItemId(orderItemId)).thenReturn(0L);
        when(ledInstanceFulfillmentPort.linkAndMarkInTransit("LMT-001", 5L))
                .thenReturn(new FulfillmentDeviceSnapshot(15L, 5L, "Matrix 64x64", "LMT-001", "AA:BB:CC:DD:EE:01", "IN_TRANSIT"));
        when(orderItemFulfillmentRepository.existsByInstanceId(15L)).thenReturn(false);

        OrderItemFulfillmentEntity savedFulfillment = OrderItemFulfillmentEntity.create(item, 15L);
        savedFulfillment.setFulfillmentId(501L);
        when(orderItemFulfillmentRepository.save(any(OrderItemFulfillmentEntity.class))).thenReturn(savedFulfillment);
        when(orderItemFulfillmentRepository.countByOrderItem_Order_OrderId(orderId)).thenReturn(1L);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(List.of(item));

        LinkOrderFulfillmentDeviceRequest request = new LinkOrderFulfillmentDeviceRequest(orderItemId, "LMT-001");
        LinkOrderFulfillmentDeviceResponse response = useCase.execute("staff-1", orderId, request);

        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.orderItemId()).isEqualTo(orderItemId);
        assertThat(response.instanceId()).isEqualTo(15L);
        assertThat(response.serialNumber()).isEqualTo("LMT-001");
        assertThat(response.macAddress()).isEqualTo("AA:BB:CC:DD:EE:01");
        assertThat(response.modelId()).isEqualTo(5L);
        assertThat(response.instanceStatus()).isEqualTo("IN_TRANSIT");
        assertThat(response.progress().isFullyFulfilled()).isTrue();
        assertThat(response.progress().itemFulfilledCount()).isEqualTo(1);
        assertThat(response.progress().itemRequiredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("COM-16 UTCID02 [P]: Successfully links LED unit inside a BUNDLE and marks order PARTIALLY_FULFILLED")
    void execute_bundleItem_linksSuccessfullyAndUpdatesStatus() {
        Long orderId = 101L;
        Long orderItemId = 202L;
        OrderEntity order = sampleOrder(orderId, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        OrderItemEntity item = OrderItemEntity.create(order, 50L, 2, new BigDecimal("1200000.00"));
        item.setOrderItemId(orderItemId);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(item));
        when(productLookupService.findSnapshotById(50L)).thenReturn(Optional.of(
                new ProductSnapshot(50L, "Starter Bundle", ProductType.BUNDLE, "thumb.png", new BigDecimal("1200000.00"), ProductStatus.PUBLISHED)
        ));

        BundleSnapshot.BundleItemSnapshot ledItem = new BundleSnapshot.BundleItemSnapshot(1L, BundleItemType.LED, 5L, null, 1);
        BundleSnapshot bundle = new BundleSnapshot(10L, 50L, "Starter Bundle", new BigDecimal("1200000.00"), "ACTIVE", null, null, List.of(ledItem));
        when(bundleLookupService.findByProductId(50L)).thenReturn(Optional.of(bundle));

        when(orderItemFulfillmentRepository.countByOrderItem_OrderItemId(orderItemId)).thenReturn(0L);
        when(ledInstanceFulfillmentPort.linkAndMarkInTransit("LMT-002", 5L))
                .thenReturn(new FulfillmentDeviceSnapshot(16L, 5L, "Matrix 64x64", "LMT-002", "AA:BB:CC:DD:EE:02", "IN_TRANSIT"));
        when(orderItemFulfillmentRepository.existsByInstanceId(16L)).thenReturn(false);

        OrderItemFulfillmentEntity savedFulfillment = OrderItemFulfillmentEntity.create(item, 16L);
        savedFulfillment.setFulfillmentId(502L);
        when(orderItemFulfillmentRepository.save(any(OrderItemFulfillmentEntity.class))).thenReturn(savedFulfillment);
        when(orderItemFulfillmentRepository.countByOrderItem_Order_OrderId(orderId)).thenReturn(1L);
        when(orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId)).thenReturn(List.of(item));

        LinkOrderFulfillmentDeviceRequest request = new LinkOrderFulfillmentDeviceRequest(orderItemId, "LMT-002");
        LinkOrderFulfillmentDeviceResponse response = useCase.execute("staff-1", orderId, request);

        assertThat(response).isNotNull();
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.PARTIALLY_FULFILLED.name());
        assertThat(response.progress().isFullyFulfilled()).isFalse();
        assertThat(response.progress().orderFulfilledCount()).isEqualTo(1);
        assertThat(response.progress().orderRequiredCount()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_FULFILLED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("COM-16 UTCID03 [A]: Throws OrderNotFoundException when order does not exist")
    void execute_orderNotFound_throwsOrderNotFoundException() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("staff-1", 999L, new LinkOrderFulfillmentDeviceRequest(201L, "LMT-001")))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found with id: 999");
    }

    @Test
    @DisplayName("COM-16 UTCID04 [A]: Throws OrderStatusTransitionException when order is in invalid status")
    void execute_invalidOrderStatus_throwsOrderStatusTransitionException() {
        OrderEntity order = sampleOrder(102L, OrderStatus.PENDING_PAYMENT, OrderType.PHYSICAL);
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(102L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute("staff-1", 102L, new LinkOrderFulfillmentDeviceRequest(201L, "LMT-001")))
                .isInstanceOf(OrderStatusTransitionException.class)
                .hasMessageContaining("Order must be in PROCESSING or PARTIALLY_FULFILLED");
    }

    @Test
    @DisplayName("COM-16 UTCID05 [A]: Throws OrderFulfillmentException when order is purely digital")
    void execute_digitalOrder_throwsOrderFulfillmentException() {
        OrderEntity order = sampleOrder(103L, OrderStatus.PROCESSING, OrderType.DIGITAL);
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(103L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute("staff-1", 103L, new LinkOrderFulfillmentDeviceRequest(201L, "LMT-001")))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ofe.getErrorCode()).isEqualTo("PHYSICAL_HARDWARE_NOT_REQUIRED");
                });
    }

    @Test
    @DisplayName("COM-16 UTCID06 [A]: Throws OrderFulfillmentException when order item does not belong to order")
    void execute_itemNotBelongingToOrder_throwsOrderFulfillmentException() {
        OrderEntity order1 = sampleOrder(104L, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        OrderEntity order2 = sampleOrder(105L, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        OrderItemEntity item = OrderItemEntity.create(order2, 5L, 1, new BigDecimal("500000.00"));
        item.setOrderItemId(203L);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(104L)).thenReturn(Optional.of(order1));
        when(orderItemRepository.findById(203L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> useCase.execute("staff-1", 104L, new LinkOrderFulfillmentDeviceRequest(203L, "LMT-001")))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ofe.getErrorCode()).isEqualTo("INVALID_ORDER_ITEM");
                });
    }

    @Test
    @DisplayName("COM-16 UTCID07 [A]: Throws OrderFulfillmentException when order item is a digital character")
    void execute_digitalItemInMixedOrder_throwsOrderFulfillmentException() {
        OrderEntity order = sampleOrder(106L, OrderStatus.PROCESSING, OrderType.MIXED);
        OrderItemEntity item = OrderItemEntity.create(order, 88L, 1, new BigDecimal("100000.00"));
        item.setOrderItemId(204L);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(106L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(204L)).thenReturn(Optional.of(item));
        when(productLookupService.findSnapshotById(88L)).thenReturn(Optional.of(
                new ProductSnapshot(88L, "Pixel Dino", ProductType.CHARACTER, "thumb.png", new BigDecimal("100000.00"), ProductStatus.PUBLISHED)
        ));

        assertThatThrownBy(() -> useCase.execute("staff-1", 106L, new LinkOrderFulfillmentDeviceRequest(204L, "LMT-001")))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ofe.getErrorCode()).isEqualTo("PHYSICAL_HARDWARE_NOT_REQUIRED");
                });
    }

    @Test
    @DisplayName("COM-16 UTCID08 [A]: Throws OrderFulfillmentException when order item is already fully fulfilled")
    void execute_alreadyFulfilledItem_throwsOrderFulfillmentException() {
        OrderEntity order = sampleOrder(107L, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        OrderItemEntity item = OrderItemEntity.create(order, 5L, 1, new BigDecimal("500000.00"));
        item.setOrderItemId(205L);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(107L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(205L)).thenReturn(Optional.of(item));
        when(productLookupService.findSnapshotById(5L)).thenReturn(Optional.of(
                new ProductSnapshot(5L, "Matrix 64x64", ProductType.LED, "thumb.png", new BigDecimal("500000.00"), ProductStatus.PUBLISHED)
        ));
        when(orderItemFulfillmentRepository.countByOrderItem_OrderItemId(205L)).thenReturn(1L);

        assertThatThrownBy(() -> useCase.execute("staff-1", 107L, new LinkOrderFulfillmentDeviceRequest(205L, "LMT-001")))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ofe.getErrorCode()).isEqualTo("ORDER_ITEM_ALREADY_FULFILLED");
                });

        verifyNoInteractions(ledInstanceFulfillmentPort);
    }

    @Test
    @DisplayName("COM-16 UTCID09 [A]: Throws OrderFulfillmentException when device instance is already assigned")
    void execute_deviceAlreadyAssigned_throwsOrderFulfillmentException() {
        OrderEntity order = sampleOrder(108L, OrderStatus.PROCESSING, OrderType.PHYSICAL);
        OrderItemEntity item = OrderItemEntity.create(order, 5L, 1, new BigDecimal("500000.00"));
        item.setOrderItemId(206L);

        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("staff-1", ALLOWED_ROLES)).thenReturn(7L);
        when(orderRepository.findById(108L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findById(206L)).thenReturn(Optional.of(item));
        when(productLookupService.findSnapshotById(5L)).thenReturn(Optional.of(
                new ProductSnapshot(5L, "Matrix 64x64", ProductType.LED, "thumb.png", new BigDecimal("500000.00"), ProductStatus.PUBLISHED)
        ));
        when(orderItemFulfillmentRepository.countByOrderItem_OrderItemId(206L)).thenReturn(0L);
        when(ledInstanceFulfillmentPort.linkAndMarkInTransit("LMT-003", 5L))
                .thenReturn(new FulfillmentDeviceSnapshot(17L, 5L, "Matrix 64x64", "LMT-003", "AA:BB:CC:DD:EE:03", "IN_TRANSIT"));
        when(orderItemFulfillmentRepository.existsByInstanceId(17L)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("staff-1", 108L, new LinkOrderFulfillmentDeviceRequest(206L, "LMT-003")))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ofe.getErrorCode()).isEqualTo("DEVICE_ALREADY_ASSIGNED");
                });
    }

    @Test
    @DisplayName("COM-16 UTCID10 [A]: Throws AccessDeniedException when caller is not an authorized employee")
    void execute_unauthorizedEmployee_throwsAccessDeniedException() {
        when(internalEmployeeAccessService.requireActiveEmployeeAccountId("intruder", ALLOWED_ROLES))
                .thenThrow(new AccessDeniedException("Forbidden"));

        assertThatThrownBy(() -> useCase.execute("intruder", 100L, new LinkOrderFulfillmentDeviceRequest(201L, "LMT-001")))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(orderRepository);
    }
}
