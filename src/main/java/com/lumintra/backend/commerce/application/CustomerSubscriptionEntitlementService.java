package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.response.CustomerActiveSubscriptionResponse;
import com.lumintra.backend.commerce.domain.service.CustomerSubscriptionBonusLookupService;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.store.domain.model.SubscriptionDetailSnapshot;
import com.lumintra.backend.store.domain.service.SubscriptionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
public class CustomerSubscriptionEntitlementService implements CustomerSubscriptionBonusLookupService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_EXHAUSTED = "EXHAUSTED";

    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final SubscriptionLookupService subscriptionLookupService;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public Optional<CustomerActiveSubscriptionResponse> findActiveSubscription(Long customerId) {
        return resolveValidActiveSubscriptions(customerId).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<CustomerActiveSubscriptionResponse> findActiveSubscriptions(Long customerId) {
        return resolveValidActiveSubscriptions(customerId);
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

    @Transactional(readOnly = true)
    public boolean hasActiveSubscriptionForProduct(Long customerId, Long productId) {
        if (customerId == null || productId == null) {
            return false;
        }
        return resolveValidActiveSubscriptions(customerId).stream()
                .anyMatch(sub -> productId.equals(sub.productId()));
    }

    /**
     * Pure commission-revision credit packs can be purchased repeatedly to top up credits;
     * all-access character plans cannot be repurchased while already active.
     */
    @Transactional(readOnly = true)
    public boolean isSubscriptionProductAlreadyOwned(Long customerId, Long productId) {
        if (customerId == null || productId == null) {
            return false;
        }
        SubscriptionDetailSnapshot detail =
                subscriptionLookupService.findByProductIds(Set.of(productId)).get(productId);
        boolean isRepeatableRevisionPack =
                detail != null && !detail.allAccessCharacters() && detail.maxCommissionRevisions() > 0;
        if (isRepeatableRevisionPack) {
            return false;
        }
        return hasActiveSubscriptionForProduct(customerId, productId);
    }

    @Override
    @Transactional(readOnly = true)
    public int findExtraCommissionRevisions(Long customerId) {
        if (customerId == null) {
            return 0;
        }
        return resolveValidActiveSubscriptions(customerId).stream()
                .mapToInt(sub -> Math.max(0, sub.remainingCommissionRevisions()))
                .sum();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consumeOneExtraCommissionRevision(Long customerId) {
        if (customerId == null) {
            return false;
        }
        List<CustomerSubscriptionEntity> candidates =
                customerSubscriptionRepository.findActiveWithRemainingRevisionsForUpdate(customerId, STATUS_ACTIVE);
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }

        Set<Long> productIds = candidates.stream()
                .map(CustomerSubscriptionEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SubscriptionDetailSnapshot> detailsByProductId =
                productIds.isEmpty() ? Map.of() : subscriptionLookupService.findByProductIds(productIds);

        Instant now = Instant.now();
        for (CustomerSubscriptionEntity sub : candidates) {
            SubscriptionDetailSnapshot detail = detailsByProductId.get(sub.getProductId());
            if (isExpired(sub, detail, now)) {
                continue;
            }
            if (sub.consumeOneRevisionCredit()) {
                boolean allAccess = detail != null && detail.allAccessCharacters();
                if (!allAccess && sub.getRemainingCommissionRevisions() <= 0) {
                    sub.setStatus(STATUS_EXHAUSTED);
                }
                customerSubscriptionRepository.save(sub);
                return true;
            }
        }
        return false;
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

    private boolean isExpired(CustomerSubscriptionEntity sub, SubscriptionDetailSnapshot detail, Instant now) {
        boolean isLifetime = detail != null ? detail.isLifetime() : sub.isLifetime();
        Integer durationDays = detail != null ? detail.durationDays() : null;
        Instant activatedAt = sub.getActivatedAt();
        if (!isLifetime && durationDays != null && durationDays > 0 && activatedAt != null) {
            Instant expiresAt = activatedAt.plus(durationDays, ChronoUnit.DAYS);
            return !now.isBefore(expiresAt);
        }
        return false;
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

        boolean allAccess = detail != null && detail.allAccessCharacters();
        int maxCommissionRevisions = detail != null ? detail.maxCommissionRevisions() : 0;
        int remainingCommissionRevisions = Math.max(0, sub.getRemainingCommissionRevisions());
        if (!allAccess && maxCommissionRevisions > 0 && remainingCommissionRevisions <= 0) {
            return Optional.empty();
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
                allAccess,
                maxCommissionRevisions,
                remainingCommissionRevisions,
                detail != null ? detail.maxMobileProfiles() : 3,
                activatedAt,
                expiresAt
        ));
    }
}
