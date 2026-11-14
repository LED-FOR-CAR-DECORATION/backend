package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.CancelOrderRequest;
import com.lumintra.backend.commerce.api.response.CancelOrderResponse;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.device.domain.service.LedInventoryService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.BundleSnapshot;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.BundleLookupService;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelOrderUseCase.class);

    private final OrderRepository orderRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductLookupService productLookupService;
    private final BundleLookupService bundleLookupService;
    private final LedInventoryService ledInventoryService;
    private final OrderMapper orderMapper;

    @Transactional
    public CancelOrderResponse execute(Long customerId, Long orderId, CancelOrderRequest request) {
        OrderEntity order = orderRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> {
                    log.warn("Customer cancel rejected: order not found or not owned by customer [orderId={}] [customerId={}]",
                            orderId, customerId);
                    return new OrderNotFoundException("Order not found.");
                });

        OrderStatus previousStatus = order.getStatus();
        order.validateCustomerCancel();
        OrderStatus targetStatus = order.resolveCustomerCancelTargetStatus();

        orderDeliveryRepository.findByOrder_OrderId(orderId).ifPresent(delivery -> {
            if (delivery.hasActiveShipment() || delivery.getTrackingNumber() != null) {
                log.warn("Customer cancel rejected: order already dispatched [orderId={}] [customerId={}] [deliveryStatus={}] [trackingNumber={}]",
                        orderId, customerId, delivery.getDeliveryStatus(), delivery.getTrackingNumber());
                throw new OrderStatusTransitionException(
                        "Order cannot be cancelled because it has already been dispatched to the delivery partner.");
            }
        });

        Instant updatedAt = Instant.now();
        int updatedRows = orderRepository.updateStatusIfCurrent(
                orderId,
                previousStatus,
                targetStatus,
                updatedAt
        );
        if (updatedRows == 0) {
            log.warn("Concurrent customer order cancel conflict [orderId={}] [customerId={}] [expectedStatus={}]",
                    orderId, customerId, previousStatus);
            throw new OrderStatusTransitionException(
                    "Order status changed concurrently and can no longer transition from "
                            + previousStatus + " to " + targetStatus + ".");
        }

        Map<Long, Long> restoredStocks = Collections.emptyMap();
        if (order.getOrderType() != OrderType.DIGITAL) {
            restoredStocks = collectLedHardwareQuantities(orderId);
            if (!restoredStocks.isEmpty()) {
                ledInventoryService.restoreStocks(restoredStocks);
            }
        }

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "none";
        log.info("Order cancelled by customer [orderId={}] [customerId={}] [previousStatus={}] [newStatus={}] [restoredLedModels={}] [reason={}]",
                orderId, customerId, previousStatus, targetStatus, restoredStocks.size(), reason);

        return orderMapper.toCancelOrderResponse(orderId, previousStatus, targetStatus, updatedAt);
    }

    private Map<Long, Long> collectLedHardwareQuantities(Long orderId) {
        List<OrderItemEntity> items = orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId);
        if (items.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> productIds = items.stream()
                .map(OrderItemEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, ProductSnapshot> productsById = productLookupService.findSnapshotsByIds(productIds);
        Map<Long, Optional<BundleSnapshot>> bundleCache = new HashMap<>();
        Map<Long, Long> quantitiesByModelId = new HashMap<>();

        for (OrderItemEntity item : items) {
            if (item.getQuantity() <= 0 || item.getProductId() == null) {
                continue;
            }
            ProductSnapshot product = productsById.get(item.getProductId());
            if (product == null) {
                log.warn("Skipping LED stock restoration for missing product snapshot [orderId={}] [productId={}]",
                        orderId, item.getProductId());
                continue;
            }

            if (product.productType() == ProductType.LED) {
                quantitiesByModelId.merge(product.productId(), (long) item.getQuantity(), Math::addExact);
            } else if (product.productType() == ProductType.BUNDLE) {
                Optional<BundleSnapshot> bundleOpt = bundleCache.computeIfAbsent(
                        product.productId(),
                        bundleLookupService::findByProductId
                );
                if (bundleOpt.isEmpty()) {
                    log.warn("Skipping bundle LED stock restoration for missing bundle snapshot [orderId={}] [productId={}]",
                            orderId, product.productId());
                    continue;
                }
                List<BundleSnapshot.BundleItemSnapshot> bundleItems = bundleOpt.get().items();
                if (bundleItems == null) {
                    continue;
                }
                for (BundleSnapshot.BundleItemSnapshot bundleItem : bundleItems) {
                    if (bundleItem.itemType() == BundleItemType.LED
                            && bundleItem.modelId() != null
                            && bundleItem.quantity() > 0) {
                        long restoreQuantity = Math.multiplyExact(
                                (long) bundleItem.quantity(),
                                (long) item.getQuantity()
                        );
                        quantitiesByModelId.merge(bundleItem.modelId(), restoreQuantity, Math::addExact);
                    }
                }
            }
        }

        return quantitiesByModelId;
    }
}
