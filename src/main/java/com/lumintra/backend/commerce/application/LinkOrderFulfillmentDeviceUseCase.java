package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.LinkOrderFulfillmentDeviceRequest;
import com.lumintra.backend.commerce.api.response.LinkOrderFulfillmentDeviceResponse;
import com.lumintra.backend.commerce.domain.model.FulfillmentDeviceSnapshot;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.service.LedInstanceFulfillmentPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemFulfillmentEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemFulfillmentRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderFulfillmentException;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.BundleSnapshot;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.BundleLookupService;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LinkOrderFulfillmentDeviceUseCase {

    private static final Logger log = LoggerFactory.getLogger(LinkOrderFulfillmentDeviceUseCase.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemFulfillmentRepository orderItemFulfillmentRepository;
    private final ProductLookupService productLookupService;
    private final BundleLookupService bundleLookupService;
    private final LedInstanceFulfillmentPort ledInstanceFulfillmentPort;

    @Transactional
    public LinkOrderFulfillmentDeviceResponse execute(
            String authenticatedSubject,
            Long orderId,
            LinkOrderFulfillmentDeviceRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Long employeeAccountId = internalEmployeeAccessService
                .requireActiveEmployeeAccountId(authenticatedSubject, ALLOWED_ROLES);

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Staff link device rejected: order not found [orderId={}] [employeeAccountId={}]",
                            orderId, employeeAccountId);
                    return new OrderNotFoundException("Order not found with id: " + orderId);
                });

        if (order.getStatus() != OrderStatus.PROCESSING && order.getStatus() != OrderStatus.PARTIALLY_FULFILLED) {
            log.warn("Staff link device rejected: invalid status [orderId={}] [status={}] [employeeAccountId={}]",
                    orderId, order.getStatus(), employeeAccountId);
            throw new OrderStatusTransitionException(
                    "Order must be in PROCESSING or PARTIALLY_FULFILLED status to link fulfillment devices. Current status: "
                            + order.getStatus()
            );
        }

        if (order.getOrderType() == OrderType.DIGITAL) {
            log.warn("Staff link device rejected: digital order [orderId={}] [employeeAccountId={}]",
                    orderId, employeeAccountId);
            throw new OrderFulfillmentException(
                    HttpStatus.BAD_REQUEST,
                    "PHYSICAL_HARDWARE_NOT_REQUIRED",
                    "Order is purely digital and does not require physical hardware fulfillment."
            );
        }

        OrderItemEntity orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new OrderFulfillmentException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_ORDER_ITEM",
                        "OrderItem " + request.orderItemId() + " not found."
                ));

        if (!Objects.equals(orderItem.getOrder().getOrderId(), orderId)) {
            log.warn("Staff link device rejected: item does not belong to order [orderItemId={}] [orderId={}]",
                    request.orderItemId(), orderId);
            throw new OrderFulfillmentException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ORDER_ITEM",
                    "OrderItem " + request.orderItemId() + " does not belong to order " + orderId + "."
            );
        }

        ProductSnapshot product = productLookupService.findSnapshotById(orderItem.getProductId())
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + orderItem.getProductId()));

        HardwareItemRequirement requirement = resolveItemRequirement(orderItem, product);

        long currentItemFulfilled = orderItemFulfillmentRepository.countByOrderItem_OrderItemId(orderItem.getOrderItemId());
        if (currentItemFulfilled >= requirement.requiredCount()) {
            log.warn("Staff link device rejected: order item already fully fulfilled [orderItemId={}] [fulfilled={}] [required={}]",
                    orderItem.getOrderItemId(), currentItemFulfilled, requirement.requiredCount());
            throw new OrderFulfillmentException(
                    HttpStatus.CONFLICT,
                    "ORDER_ITEM_ALREADY_FULFILLED",
                    "OrderItem " + orderItem.getOrderItemId() + " has already been fully fulfilled ("
                            + currentItemFulfilled + "/" + requirement.requiredCount() + " units)."
            );
        }

        FulfillmentDeviceSnapshot device = ledInstanceFulfillmentPort.linkAndMarkInTransit(
                request.serialNumber(),
                requirement.expectedModelId()
        );

        if (orderItemFulfillmentRepository.existsByInstanceId(device.instanceId())) {
            log.warn("Staff link device rejected: device already assigned [instanceId={}]", device.instanceId());
            throw new OrderFulfillmentException(
                    HttpStatus.CONFLICT,
                    "DEVICE_ALREADY_ASSIGNED",
                    "LED instance " + device.instanceId() + " is already assigned to an order item."
            );
        }

        OrderItemFulfillmentEntity fulfillment = OrderItemFulfillmentEntity.create(orderItem, device.instanceId());
        OrderItemFulfillmentEntity savedFulfillment = orderItemFulfillmentRepository.save(fulfillment);

        long updatedItemFulfilled = currentItemFulfilled + 1;
        long totalOrderFulfilled = orderItemFulfillmentRepository.countByOrderItem_Order_OrderId(orderId);
        long totalOrderRequired = calculateTotalRequiredLedUnits(orderId);
        boolean isFullyFulfilled = totalOrderFulfilled >= totalOrderRequired;

        // Synchronize order status
        if (!isFullyFulfilled && order.getStatus() != OrderStatus.PARTIALLY_FULFILLED) {
            order.setStatus(OrderStatus.PARTIALLY_FULFILLED);
            orderRepository.save(order);
        } else if (isFullyFulfilled && order.getStatus() == OrderStatus.PARTIALLY_FULFILLED) {
            order.setStatus(OrderStatus.PROCESSING);
            orderRepository.save(order);
        }

        log.info("LED instance successfully linked to order item [orderId={}] [orderItemId={}] [instanceId={}] [employeeAccountId={}] [progress={}/{}]",
                orderId, orderItem.getOrderItemId(), device.instanceId(), employeeAccountId, totalOrderFulfilled, totalOrderRequired);

        return new LinkOrderFulfillmentDeviceResponse(
                orderId,
                order.getStatus().name(),
                savedFulfillment.getFulfillmentId(),
                orderItem.getOrderItemId(),
                device.instanceId(),
                device.serialNumber(),
                device.macAddress(),
                device.modelId(),
                device.modelName(),
                device.status(),
                new LinkOrderFulfillmentDeviceResponse.FulfillmentProgress(
                        updatedItemFulfilled,
                        requirement.requiredCount(),
                        totalOrderFulfilled,
                        totalOrderRequired,
                        isFullyFulfilled
                ),
                savedFulfillment.getCreatedAt() != null ? savedFulfillment.getCreatedAt() : Instant.now()
        );
    }

    public long calculateTotalRequiredLedUnits(Long orderId) {
        List<OrderItemEntity> orderItems = orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId);
        long total = 0;
        for (OrderItemEntity item : orderItems) {
            ProductSnapshot product = productLookupService.findSnapshotById(item.getProductId()).orElse(null);
            if (product == null) {
                continue;
            }
            if (product.productType() == ProductType.LED) {
                total += item.getQuantity();
            } else if (product.productType() == ProductType.BUNDLE) {
                BundleSnapshot bundle = bundleLookupService.findByProductId(product.productId()).orElse(null);
                if (bundle != null && bundle.items() != null) {
                    for (BundleSnapshot.BundleItemSnapshot bi : bundle.items()) {
                        if (bi.itemType() == BundleItemType.LED && bi.modelId() != null) {
                            total += (long) bi.quantity() * item.getQuantity();
                        }
                    }
                }
            }
        }
        return total;
    }

    private HardwareItemRequirement resolveItemRequirement(OrderItemEntity orderItem, ProductSnapshot product) {
        if (product.productType() == ProductType.LED) {
            return new HardwareItemRequirement(product.productId(), orderItem.getQuantity());
        }

        if (product.productType() == ProductType.BUNDLE) {
            BundleSnapshot bundle = bundleLookupService.findByProductId(product.productId())
                    .orElseThrow(() -> new OrderFulfillmentException(
                            HttpStatus.BAD_REQUEST,
                            "PHYSICAL_HARDWARE_NOT_REQUIRED",
                            "Bundle information not found for product: " + product.productId()
                    ));

            List<BundleSnapshot.BundleItemSnapshot> ledItems = bundle.items() != null
                    ? bundle.items().stream()
                    .filter(bi -> bi.itemType() == BundleItemType.LED && bi.modelId() != null)
                    .toList()
                    : List.of();

            if (ledItems.isEmpty()) {
                throw new OrderFulfillmentException(
                        HttpStatus.BAD_REQUEST,
                        "PHYSICAL_HARDWARE_NOT_REQUIRED",
                        "OrderItem " + orderItem.getOrderItemId() + " does not contain physical LED hardware."
                );
            }

            BundleSnapshot.BundleItemSnapshot primaryLed = ledItems.getFirst();
            long requiredCount = (long) primaryLed.quantity() * orderItem.getQuantity();
            return new HardwareItemRequirement(primaryLed.modelId(), requiredCount);
        }

        throw new OrderFulfillmentException(
                HttpStatus.BAD_REQUEST,
                "PHYSICAL_HARDWARE_NOT_REQUIRED",
                "OrderItem " + orderItem.getOrderItemId() + " is a digital item and does not require physical hardware fulfillment."
        );
    }

    private record HardwareItemRequirement(Long expectedModelId, long requiredCount) {}
}
