package com.lumintra.backend.commerce.infrastructure.persistence;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemFulfillmentEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemFulfillmentRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderFulfillmentLookupServiceImpl implements OrderFulfillmentLookupService {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentLookupServiceImpl.class);

    private final OrderItemFulfillmentRepository orderItemFulfillmentRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, InstanceFulfillmentSnapshot> findFulfillmentsByInstanceIds(Collection<Long> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> validIds = instanceIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<OrderItemFulfillmentEntity> fulfillments =
                orderItemFulfillmentRepository.findAllWithOrderByInstanceIdIn(validIds);
        if (fulfillments.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, InstanceFulfillmentSnapshot> result = new LinkedHashMap<>(fulfillments.size());
        for (OrderItemFulfillmentEntity entity : fulfillments) {
            result.put(entity.getInstanceId(), toSnapshot(entity));
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InstanceFulfillmentSnapshot> findFulfillmentByInstanceId(Long instanceId) {
        if (instanceId == null) {
            return Optional.empty();
        }
        return orderItemFulfillmentRepository.findWithOrderByInstanceId(instanceId)
                .map(this::toSnapshot);
    }

    @Override
    @Transactional
    public void seedOrderFulfillmentIfPresent(String transactionCodeSuffix, Long instanceId) {
        if (transactionCodeSuffix == null || transactionCodeSuffix.isBlank() || instanceId == null) {
            return;
        }
        if (orderItemFulfillmentRepository.existsByInstanceId(instanceId)) {
            return;
        }
        Optional<PaymentTransactionEntity> paymentOpt = paymentTransactionRepository
                .findFirstByTransactionCodeEndingWithOrderByPaymentIdDesc(transactionCodeSuffix.strip());
        if (paymentOpt.isEmpty()) {
            return;
        }

        Long orderId = paymentOpt.get().getOrderId();
        List<OrderItemEntity> items = orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId);
        if (items.isEmpty()) {
            return;
        }

        OrderItemEntity targetItem = items.get(0);
        OrderItemFulfillmentEntity saved = orderItemFulfillmentRepository.save(
                OrderItemFulfillmentEntity.create(targetItem, instanceId));
        log.info("Seeded order item fulfillment link [fulfillmentId={}] [orderId={}] [orderItemId={}] [instanceId={}]",
                saved.getFulfillmentId(), orderId, targetItem.getOrderItemId(), instanceId);
    }

    private InstanceFulfillmentSnapshot toSnapshot(OrderItemFulfillmentEntity entity) {
        OrderItemEntity orderItem = entity.getOrderItem();
        OrderEntity order = orderItem != null ? orderItem.getOrder() : null;
        return new InstanceFulfillmentSnapshot(
                entity.getInstanceId(),
                order != null ? order.getOrderId() : null,
                orderItem != null ? orderItem.getOrderItemId() : null,
                order != null ? order.getCustomerId() : null,
                order != null && order.getStatus() != null ? order.getStatus().name() : null
        );
    }
}
