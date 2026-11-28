package com.lumintra.backend.commerce.application.listener;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OwnedCharacterRepository;
import com.lumintra.backend.payment.domain.event.PaymentSucceededEvent;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.BundleSnapshot;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.model.SubscriptionDetailSnapshot;
import com.lumintra.backend.store.domain.service.BundleLookupService;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import com.lumintra.backend.store.domain.service.SubscriptionLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Listener for PaymentSucceededEvent to transition paid Commerce orders and provision
 * any digital entitlements (characters, subscriptions, bundle contents).
 */
@Component
@RequiredArgsConstructor
public class OrderPaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentEventListener.class);
    private static final String SUBSCRIPTION_STATUS_ACTIVE = "ACTIVE";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OwnedCharacterRepository ownedCharacterRepository;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final ProductLookupService productLookupService;
    private final CharacterLookupService characterLookupService;
    private final BundleLookupService bundleLookupService;
    private final SubscriptionLookupService subscriptionLookupService;

    @EventListener
    @Transactional
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null) {
            return;
        }

        if (event.targetType() != PaymentTargetType.ORDER) {
            log.debug("PaymentSucceededEvent ignored: targetType is {} [paymentId={}] [targetId={}]",
                    event.targetType(), event.paymentId(), event.targetId());
            return;
        }

        Long orderId = event.targetId();
        if (orderId == null) {
            log.warn("PaymentSucceededEvent ignored: targetId is null [paymentId={}]", event.paymentId());
            return;
        }

        Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for PaymentSucceededEvent [orderId={}] [paymentId={}]", orderId, event.paymentId());
            return;
        }

        OrderEntity order = orderOpt.get();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.info("Order payment transition skipped: order is in non-pending status {} [orderId={}] [paymentId={}]",
                    order.getStatus(), orderId, event.paymentId());
            return;
        }

        List<OrderItemEntity> items = orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId);
        Map<Long, ProductSnapshot> productMap = Map.of();
        Map<Long, BundleSnapshot> bundlesByProductId = Map.of();

        if (!items.isEmpty()) {
            Set<Long> productIds = items.stream()
                    .map(OrderItemEntity::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            productMap = productLookupService.findSnapshotsByIds(productIds);
            bundlesByProductId = preloadBundleSnapshots(items, productMap);
        }

        OrderStatus targetStatus = resolveTargetStatus(order, items, productMap, bundlesByProductId);
        Instant now = Instant.now();
        int updated = orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PENDING_PAYMENT, targetStatus, now);
        if (updated == 0) {
            log.warn("Order payment transition skipped: concurrent update detected [orderId={}] [paymentId={}]",
                    orderId, event.paymentId());
            return;
        }

        log.info("Order marked paid: [orderId={}] [previousStatus={}] [newStatus={}] [orderType={}]",
                orderId, OrderStatus.PENDING_PAYMENT, targetStatus, order.getOrderType());

        provisionDigitalEntitlements(order, items, productMap, bundlesByProductId);
    }

    private Map<Long, BundleSnapshot> preloadBundleSnapshots(
            List<OrderItemEntity> items,
            Map<Long, ProductSnapshot> productMap) {
        Map<Long, BundleSnapshot> result = new HashMap<>();
        for (OrderItemEntity item : items) {
            ProductSnapshot product = productMap.get(item.getProductId());
            if (product != null && product.productType() == ProductType.BUNDLE
                    && !result.containsKey(item.getProductId())) {
                bundleLookupService.findByProductId(item.getProductId())
                        .ifPresent(bundle -> result.put(item.getProductId(), bundle));
            }
        }
        return result;
    }

    private OrderStatus resolveTargetStatus(
            OrderEntity order,
            List<OrderItemEntity> items,
            Map<Long, ProductSnapshot> productMap,
            Map<Long, BundleSnapshot> bundlesByProductId) {
        if (items.isEmpty()) {
            return order.resolvePaymentSuccessTargetStatus();
        }

        boolean hasPhysicalGoods = false;
        boolean hasDigitalGoods = false;

        for (OrderItemEntity item : items) {
            ProductSnapshot product = productMap.get(item.getProductId());
            if (product == null || product.productType() == null) {
                continue;
            }
            switch (product.productType()) {
                case LED -> hasPhysicalGoods = true;
                case CHARACTER, SUBSCRIPTION -> hasDigitalGoods = true;
                case BUNDLE -> {
                    BundleSnapshot bundle = bundlesByProductId.get(item.getProductId());
                    if (bundle == null || bundle.items() == null || bundle.items().isEmpty()) {
                        hasPhysicalGoods = true;
                    } else {
                        for (BundleSnapshot.BundleItemSnapshot bundleItem : bundle.items()) {
                            if (bundleItem.itemType() == BundleItemType.LED) {
                                hasPhysicalGoods = true;
                            } else if (bundleItem.itemType() == BundleItemType.CHARACTER) {
                                hasDigitalGoods = true;
                            }
                        }
                    }
                }
            }
        }

        if (!hasPhysicalGoods && !hasDigitalGoods) {
            return order.resolvePaymentSuccessTargetStatus();
        }
        return OrderEntity.resolvePaymentSuccessTargetStatus(hasPhysicalGoods, hasDigitalGoods);
    }

    private void provisionDigitalEntitlements(
            OrderEntity order,
            List<OrderItemEntity> items,
            Map<Long, ProductSnapshot> productMap,
            Map<Long, BundleSnapshot> bundlesByProductId) {
        Long customerId = order.getCustomerId();
        if (customerId == null) {
            log.warn("Cannot provision digital entitlements: customerId is null [orderId={}]", order.getOrderId());
            return;
        }

        if (items.isEmpty()) {
            return;
        }

        for (OrderItemEntity item : items) {
            ProductSnapshot product = productMap.get(item.getProductId());
            if (product == null || product.productType() == null) {
                continue;
            }

            switch (product.productType()) {
                case CHARACTER -> provisionCharacter(customerId, item);
                case SUBSCRIPTION -> provisionSubscription(customerId, item);
                case BUNDLE -> provisionBundle(customerId, item, bundlesByProductId.get(item.getProductId()));
                case LED -> {
                    // Physical hardware LED item, no digital entitlement
                }
            }
        }
    }

    private void provisionCharacter(Long customerId, OrderItemEntity item) {
        Optional<Long> versionIdOpt = characterLookupService.findVersionIdByProductId(item.getProductId());
        if (versionIdOpt.isEmpty()) {
            log.warn("No versionId found for character product [productId={}] [orderItemId={}]",
                    item.getProductId(), item.getOrderItemId());
            return;
        }

        Long versionId = versionIdOpt.get();
        if (!ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, versionId)) {
            ownedCharacterRepository.save(OwnedCharacterEntity.create(customerId, versionId, item.getOrderItemId()));
            log.info("Provisioned character to customer [customerId={}] [versionId={}] [orderItemId={}]",
                    customerId, versionId, item.getOrderItemId());
        } else {
            log.info("Character already owned by customer [customerId={}] [versionId={}]", customerId, versionId);
        }
    }

    private void provisionSubscription(Long customerId, OrderItemEntity item) {
        if (item.getOrderItemId() != null
                && customerSubscriptionRepository.existsByOrderItemId(item.getOrderItemId())) {
            log.info("Subscription already provisioned for orderItem [orderItemId={}]", item.getOrderItemId());
            return;
        }

        SubscriptionDetailSnapshot detail =
                subscriptionLookupService.findByProductIds(Set.of(item.getProductId())).get(item.getProductId());
        boolean isLifetime = detail == null || detail.isLifetime();
        int maxRevisions = detail != null ? Math.max(0, detail.maxCommissionRevisions()) : 0;

        boolean hasActiveNonExpiredSameProduct = customerSubscriptionRepository
                .findByCustomerIdAndStatusOrderByActivatedAtDesc(customerId, SUBSCRIPTION_STATUS_ACTIVE)
                .stream()
                .filter(existing -> Objects.equals(existing.getProductId(), item.getProductId()))
                .anyMatch(existing -> {
                    if (isLifetime) {
                        return true;
                    }
                    Integer durationDays = detail.durationDays();
                    Instant activatedAt = existing.getActivatedAt();
                    if (durationDays == null || durationDays <= 0 || activatedAt == null) {
                        return true;
                    }
                    return Instant.now().isBefore(activatedAt.plus(durationDays, java.time.temporal.ChronoUnit.DAYS));
                });

        if (!hasActiveNonExpiredSameProduct) {
            CustomerSubscriptionEntity subscription = CustomerSubscriptionEntity.createActive(
                    customerId, item.getProductId(), item.getOrderItemId(), maxRevisions
            );
            subscription.setLifetime(isLifetime);
            customerSubscriptionRepository.save(subscription);
            log.info("Provisioned subscription to customer [customerId={}] [productId={}] [orderItemId={}] [isLifetime={}]",
                    customerId, item.getProductId(), item.getOrderItemId(), isLifetime);
        } else {
            log.info("Active subscription for product already exists for customer [customerId={}] [productId={}]",
                    customerId, item.getProductId());
        }
    }

    private void provisionBundle(Long customerId, OrderItemEntity item, BundleSnapshot preloadedBundle) {
        BundleSnapshot bundle = preloadedBundle != null
                ? preloadedBundle
                : bundleLookupService.findByProductId(item.getProductId()).orElse(null);
        if (bundle == null || bundle.items() == null) {
            return;
        }

        for (BundleSnapshot.BundleItemSnapshot bundleItem : bundle.items()) {
            if (bundleItem.itemType() == BundleItemType.CHARACTER && bundleItem.versionId() != null) {
                Long versionId = bundleItem.versionId();
                if (!ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, versionId)) {
                    ownedCharacterRepository.save(OwnedCharacterEntity.create(customerId, versionId, item.getOrderItemId()));
                    log.info("Provisioned bundle character to customer [customerId={}] [versionId={}] [orderItemId={}]",
                            customerId, versionId, item.getOrderItemId());
                } else {
                    log.info("Bundle character already owned by customer [customerId={}] [versionId={}]",
                            customerId, versionId);
                }
            }
        }
    }
}
