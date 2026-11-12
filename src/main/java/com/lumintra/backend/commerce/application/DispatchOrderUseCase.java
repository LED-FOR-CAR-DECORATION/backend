package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.DispatchOrderRequest;
import com.lumintra.backend.commerce.api.response.DispatchOrderResponse;
import com.lumintra.backend.commerce.domain.model.CarrierDispatchCommand;
import com.lumintra.backend.commerce.domain.model.CarrierDispatchResult;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.service.CarrierDispatchPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispatchOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(DispatchOrderUseCase.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");
    private static final Set<DeliveryStatus> DISPATCH_ELIGIBLE_DELIVERY_STATUSES = Set.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.FAILED,
            DeliveryStatus.RETURNED
    );

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final OrderRepository orderRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductLookupService productLookupService;
    private final CarrierDispatchPort carrierDispatchPort;
    private final OrderMapper orderMapper;
    private final TransactionTemplate transactionTemplate;

    public DispatchOrderResponse execute(
            String authenticatedSubject,
            Long orderId,
            DispatchOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Long employeeAccountId = internalEmployeeAccessService
                .requireActiveEmployeeAccountId(authenticatedSubject, ALLOWED_ROLES);

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Staff dispatch order not found [orderId={}] [employeeAccountId={}]",
                            orderId, employeeAccountId);
                    return new OrderNotFoundException("Order not found.");
                });

        OrderStatus previousStatus = order.getStatus();
        order.validateDispatchEligibility();

        OrderDeliveryEntity delivery = orderDeliveryRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> {
                    log.warn("Staff dispatch rejected because order has no delivery record [orderId={}] [employeeAccountId={}]",
                            orderId, employeeAccountId);
                    return new OrderStatusTransitionException(
                            "Order does not have a physical delivery record to dispatch.");
                });

        delivery.validateReadyForDispatch();

        CarrierDispatchCommand command = buildDispatchCommand(orderId, delivery, request);

        // Execute external AhaMove HTTP call outside any database transaction.
        CarrierDispatchResult dispatchResult = carrierDispatchPort.createDeliveryOrder(command);

        DispatchOrderResponse response = transactionTemplate.execute(status -> {
            Instant updatedAt = Instant.now();

            int orderUpdated = orderRepository.updateStatusIfCurrent(
                    orderId,
                    previousStatus,
                    OrderStatus.SHIPPING,
                    updatedAt
            );
            if (orderUpdated == 0) {
                log.warn("Concurrent order status conflict during dispatch [orderId={}] [expectedStatus={}] [employeeAccountId={}]",
                        orderId, previousStatus, employeeAccountId);
                throw new OrderStatusTransitionException(
                        "Order status changed concurrently and can no longer transition from "
                                + previousStatus + " to SHIPPING.");
            }

            int deliveryUpdated = orderDeliveryRepository.updateDispatchInfoIfEligible(
                    orderId,
                    DISPATCH_ELIGIBLE_DELIVERY_STATUSES,
                    DeliveryStatus.ASSIGNED,
                    dispatchResult.trackingNumber(),
                    dispatchResult.trackingUrl(),
                    dispatchResult.carrierFee(),
                    updatedAt
            );
            if (deliveryUpdated == 0) {
                log.warn("Concurrent delivery conflict during dispatch [orderId={}] [employeeAccountId={}]",
                        orderId, employeeAccountId);
                throw new OrderStatusTransitionException(
                        "Order delivery state changed concurrently and already has an active shipment.");
            }

            return orderMapper.toDispatchOrderResponse(
                    orderId,
                    OrderStatus.SHIPPING,
                    DeliveryStatus.ASSIGNED,
                    dispatchResult.trackingNumber(),
                    dispatchResult.trackingUrl(),
                    dispatchResult.carrierFee()
            );
        });

        log.info("Order dispatched to AhaMove [orderId={}] [employeeAccountId={}] [previousStatus={}] [newStatus={}] [deliveryStatus={}] [trackingNumber={}]",
                orderId,
                employeeAccountId,
                previousStatus,
                OrderStatus.SHIPPING,
                DeliveryStatus.ASSIGNED,
                dispatchResult.trackingNumber());

        return Objects.requireNonNull(response, "dispatch response must not be null");
    }

    private CarrierDispatchCommand buildDispatchCommand(
            Long orderId,
            OrderDeliveryEntity delivery,
            DispatchOrderRequest request) {
        List<OrderItemEntity> orderItems = orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId);
        Set<Long> productIds = orderItems.stream()
                .map(OrderItemEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, ProductSnapshot> productsById = productIds.isEmpty()
                ? Collections.emptyMap()
                : productLookupService.findSnapshotsByIds(productIds);

        List<CarrierDispatchCommand.DispatchItem> dispatchItems = orderItems.stream()
                .map(item -> {
                    ProductSnapshot product = productsById.get(item.getProductId());
                    String itemName = product != null && product.productName() != null
                            ? product.productName()
                            : "Product #" + item.getProductId();
                    return new CarrierDispatchCommand.DispatchItem(
                            item.getProductId(),
                            itemName,
                            item.getQuantity(),
                            item.getSubtotal()
                    );
                })
                .toList();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String internalTrackingNumber = "LUM-" + orderId + "-" + suffix;

        return new CarrierDispatchCommand(
                orderId,
                request.serviceId(),
                request.remarks(),
                request.weight(),
                internalTrackingNumber,
                delivery.getRecipientName(),
                delivery.getRecipientPhone(),
                delivery.getFullAddress(),
                delivery.getLatitude(),
                delivery.getLongitude(),
                dispatchItems
        );
    }
}
