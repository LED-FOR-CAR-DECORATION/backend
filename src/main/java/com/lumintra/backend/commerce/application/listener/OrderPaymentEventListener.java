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
import com.lumintra.backend.store.domain.service.BundleLookupService;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

        OrderStatus targetStatus = order.resolvePaymentSuccessTargetStatus();
        Instant now = Instant.now();
        int updated = orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PENDING_PAYMENT, targetStatus, now);
        if (updated == 0) {
            log.warn("Order payment transition skipped: concurrent update detected [orderId={}] [paymentId={}]",
                    orderId, event.paymentId());
            return;
        }

        log.info("Order marked paid: [orderId={}] [previousStatus={}] [newStatus={}] [orderType={}]",
                orderId, OrderStatus.PENDING_PAYMENT, targetStatus, order.getOrderType());

        provisionDigitalEntitlements(order);
    }

    private void provisionDigitalEntitlements(OrderEntity order) {
        Long customerId = order.getCustomerId();
        if (customerId == null) {
            log.warn("Cannot provision digital entitlements: customerId is null [orderId={}]", order.getOrderId());
            return;
        }

        List<OrderItemEntity> items = orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(order.getOrderId());
        if (items.isEmpty()) {
            return;
        }

        Set<Long> productIds = items.stream()
                .map(OrderItemEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, ProductSnapshot> productMap = productLookupService.findSnapshotsByIds(productIds);

        for (OrderItemEntity item : items) {
            ProductSnapshot product = productMap.get(item.getProductId());
            if (product == null || product.productType() == null) {
                continue;
            }

            switch (product.productType()) {
                case CHARACTER -> provisionCharacter(customerId, item);
                case SUBSCRIPTION -> provisionSubscription(customerId, item);
                case BUNDLE -> provisionBundle(customerId, item);
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
        if (!customerSubscriptionRepository.existsByCustomerIdAndStatus(customerId, SUBSCRIPTION_STATUS_ACTIVE)) {
            customerSubscriptionRepository.save(CustomerSubscriptionEntity.createActive(
                    customerId, item.getProductId(), item.getOrderItemId()
            ));
            log.info("Provisioned subscription to customer [customerId={}] [productId={}] [orderItemId={}]",
                    customerId, item.getProductId(), item.getOrderItemId());
        } else {
            log.info("Active subscription already exists for customer [customerId={}]", customerId);
        }
    }

    private void provisionBundle(Long customerId, OrderItemEntity item) {
        Optional<BundleSnapshot> bundleOpt = bundleLookupService.findByProductId(item.getProductId());
        if (bundleOpt.isEmpty() || bundleOpt.get().items() == null) {
            return;
        }

        for (BundleSnapshot.BundleItemSnapshot bundleItem : bundleOpt.get().items()) {
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
