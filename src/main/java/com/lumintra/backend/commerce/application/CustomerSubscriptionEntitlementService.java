package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.response.CustomerActiveSubscriptionResponse;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.store.domain.model.SubscriptionDetailSnapshot;
import com.lumintra.backend.store.domain.service.SubscriptionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerSubscriptionEntitlementService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final SubscriptionLookupService subscriptionLookupService;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public Optional<CustomerActiveSubscriptionResponse> findActiveSubscription(Long customerId) {
        return resolveValidActiveSubscriptions(customerId).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<CustomerActiveSubscriptionResponse> findActiveAllAccessSubscription(Long customerId) {
        return resolveValidActiveSubscriptions(customerId).stream()
                .filter(CustomerActiveSubscriptionResponse::allAccessCharacters)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveAllAccessCharacters(Long customerId) {
        if (customerId == null) {
            return false;
        }
        return findActiveAllAccessSubscription(customerId).isPresent();
    }

    private List<CustomerActiveSubscriptionResponse> resolveValidActiveSubscriptions(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        List<CustomerSubscriptionEntity> activeSubs =
                customerSubscriptionRepository.findByCustomerIdAndStatusOrderByActivatedAtDesc(customerId, STATUS_ACTIVE);
        if (activeSubs == null || activeSubs.isEmpty()) {
            return List.of();
        }

        Set<Long> productIds = activeSubs.stream()
                .map(CustomerSubscriptionEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SubscriptionDetailSnapshot> detailsByProductId =
                productIds.isEmpty() ? Map.of() : subscriptionLookupService.findByProductIds(productIds);

        Set<Long> orderItemIds = activeSubs.stream()
                .map(CustomerSubscriptionEntity::getOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, OrderItemEntity> orderItemsById = orderItemIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findWithOrderByOrderItemIdIn(orderItemIds).stream()
                        .collect(Collectors.toMap(OrderItemEntity::getOrderItemId, oi -> oi, (a, b) -> a));

        Instant now = Instant.now();
        return activeSubs.stream()
                .map(sub -> toResponseIfValid(sub, detailsByProductId.get(sub.getProductId()), orderItemsById, now))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<CustomerActiveSubscriptionResponse> toResponseIfValid(
            CustomerSubscriptionEntity sub,
            SubscriptionDetailSnapshot detail,
            Map<Long, OrderItemEntity> orderItemsById,
            Instant now) {
        boolean isLifetime = detail != null ? detail.isLifetime() : sub.isLifetime();
        Integer durationDays = detail != null ? detail.durationDays() : null;
        Instant activatedAt = sub.getActivatedAt();
        Instant expiresAt = null;
        if (!isLifetime && durationDays != null && durationDays > 0 && activatedAt != null) {
            expiresAt = activatedAt.plus(durationDays, ChronoUnit.DAYS);
            if (!now.isBefore(expiresAt)) {
                return Optional.empty();
            }
        }

        Long orderId = null;
        if (sub.getOrderItemId() != null) {
            OrderItemEntity orderItem = orderItemsById.get(sub.getOrderItemId());
            if (orderItem != null && orderItem.getOrder() != null) {
                orderId = orderItem.getOrder().getOrderId();
            }
        }

        return Optional.of(new CustomerActiveSubscriptionResponse(
                sub.getSubscriptionId(),
                sub.getProductId(),
                orderId,
                detail != null ? detail.planCode() : null,
                detail != null ? detail.planName() : null,
                isLifetime,
                durationDays,
                detail != null && detail.allAccessCharacters(),
                detail != null ? detail.maxCommissionRevisions() : 0,
                detail != null ? detail.maxMobileProfiles() : 3,
                activatedAt,
                expiresAt
        ));
    }
}
