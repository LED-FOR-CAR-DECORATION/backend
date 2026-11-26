package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.response.CustomerActiveSubscriptionResponse;
import com.lumintra.backend.commerce.api.response.CustomerSubscriptionBenefitsResponse;
import com.lumintra.backend.commerce.domain.service.CustomerSubscriptionBonusLookupService;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.model.SubscriptionDetailSnapshot;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import com.lumintra.backend.store.domain.service.SubscriptionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final int DEFAULT_MOBILE_PROFILES = 3;
    private static final int DEFAULT_COMMISSION_REVISIONS = 1;

    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final SubscriptionLookupService subscriptionLookupService;
    private final OrderItemRepository orderItemRepository;
    private final ProductLookupService productLookupService;

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
     * Tier-based ownership check:
     * - Prevents repurchasing a plan that is already active for the customer.
     * - Prevents purchasing an all-access-character-only plan when the customer already owns
     *   any active all-access plan (such as Ultimate).
     * - Allows upgrading from an all-access-character-only plan to an expanded all-access plan
     *   (e.g. Ultimate with 20 mobile profiles + 10 free commission revisions).
     */
    @Transactional(readOnly = true)
    public boolean isSubscriptionProductAlreadyOwned(Long customerId, Long productId) {
        if (customerId == null || productId == null) {
            return false;
        }
        List<CustomerActiveSubscriptionResponse> activeSubs = resolveValidActiveSubscriptions(customerId);
        if (activeSubs.isEmpty()) {
            return false;
        }
        if (activeSubs.stream().anyMatch(sub -> productId.equals(sub.productId()))) {
            return true;
        }

        SubscriptionDetailSnapshot targetDetail =
                subscriptionLookupService.findByProductIds(Set.of(productId)).get(productId);
        if (isAllAccessOnlyPlan(targetDetail)) {
            return activeSubs.stream().anyMatch(CustomerActiveSubscriptionResponse::allAccessCharacters);
        }
        return false;
    }

    /**
     * Resolves the effective purchase price for a subscription product.
     * When the customer already holds an active all-access-character-only plan (e.g. 999,999 VND)
     * and purchases an expanded all-access plan (e.g. Ultimate 4,999,999 VND), the price of the
     * owned all-access-only plan is deducted from the target plan price.
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveEffectiveSubscriptionPrice(Long customerId, ProductSnapshot targetProduct) {
        if (targetProduct == null || targetProduct.basePrice() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal basePrice = targetProduct.basePrice().setScale(2, RoundingMode.HALF_UP);
        if (customerId == null || targetProduct.productType() != ProductType.SUBSCRIPTION) {
            return basePrice;
        }

        SubscriptionDetailSnapshot targetDetail =
                subscriptionLookupService.findByProductIds(Set.of(targetProduct.productId()))
                        .get(targetProduct.productId());
        if (!isAllAccessUpgradePlan(targetDetail)) {
            return basePrice;
        }

        List<CustomerActiveSubscriptionResponse> activeSubs = resolveValidActiveSubscriptions(customerId);
        boolean alreadyOwnsAllAccessUpgrade = activeSubs.stream().anyMatch(this::isAllAccessUpgradePlan);
        if (alreadyOwnsAllAccessUpgrade) {
            return basePrice;
        }

        BigDecimal deduction = activeSubs.stream()
                .filter(this::isAllAccessOnlyPlan)
                .map(CustomerActiveSubscriptionResponse::paidPrice)
                .filter(Objects::nonNull)
                .filter(price -> price.signum() > 0)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        if (deduction.signum() <= 0) {
            return basePrice;
        }
        return basePrice.subtract(deduction).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public int findMaxFreeCommissionRevisions(Long customerId) {
        if (customerId == null) {
            return 0;
        }
        return resolveValidActiveSubscriptions(customerId).stream()
                .mapToInt(sub -> Math.max(0, sub.maxCommissionRevisions()))
                .max()
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public int findMaxMobileProfiles(Long customerId) {
        if (customerId == null) {
            return DEFAULT_MOBILE_PROFILES;
        }
        return resolveValidActiveSubscriptions(customerId).stream()
                .mapToInt(sub -> Math.max(DEFAULT_MOBILE_PROFILES, sub.maxMobileProfiles()))
                .max()
                .orElse(DEFAULT_MOBILE_PROFILES);
    }

    @Transactional(readOnly = true)
    public CustomerSubscriptionBenefitsResponse getCustomerSubscriptionBenefits(Long customerId) {
        if (customerId == null) {
            return emptyBenefits();
        }
        List<CustomerSubscriptionEntity> allSubs =
                customerSubscriptionRepository.findByCustomerIdOrderByActivatedAtDesc(customerId);
        if (allSubs == null || allSubs.isEmpty()) {
            return emptyBenefits();
        }

        Set<Long> productIds = allSubs.stream()
                .map(CustomerSubscriptionEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SubscriptionDetailSnapshot> detailsByProductId =
                productIds.isEmpty() ? Map.of() : subscriptionLookupService.findByProductIds(productIds);
        Map<Long, ProductSnapshot> productsById =
                (productIds.isEmpty() || productLookupService == null)
                        ? Map.of()
                        : Optional.ofNullable(productLookupService.findSnapshotsByIds(productIds)).orElse(Map.of());

        Set<Long> orderItemIds = allSubs.stream()
                .map(CustomerSubscriptionEntity::getOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, OrderItemEntity> orderItemsById = orderItemIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findWithOrderByOrderItemIdIn(orderItemIds).stream()
                        .collect(Collectors.toMap(OrderItemEntity::getOrderItemId, oi -> oi, (a, b) -> a));

        Instant now = Instant.now();
        List<CustomerActiveSubscriptionResponse> activeList = new java.util.ArrayList<>();
        List<CustomerSubscriptionBenefitsResponse.SubscriptionHistoryItem> historyList = new java.util.ArrayList<>();

        for (CustomerSubscriptionEntity sub : allSubs) {
            SubscriptionDetailSnapshot detail = detailsByProductId.get(sub.getProductId());
            ProductSnapshot product = productsById.get(sub.getProductId());
            boolean isLifetime = detail != null ? detail.isLifetime() : sub.isLifetime();
            Integer durationDays = detail != null ? detail.durationDays() : null;
            Instant activatedAt = sub.getActivatedAt();
            Instant expiresAt = null;
            boolean expired = false;
            if (!isLifetime && durationDays != null && durationDays > 0 && activatedAt != null) {
                expiresAt = activatedAt.plus(durationDays, ChronoUnit.DAYS);
                expired = !now.isBefore(expiresAt);
            }

            boolean allAccess = detail != null && detail.allAccessCharacters();
            int maxCommissionRevisions = detail != null ? Math.max(0, detail.maxCommissionRevisions()) : 0;
            int maxMobileProfiles = detail != null
                    ? Math.max(DEFAULT_MOBILE_PROFILES, detail.maxMobileProfiles())
                    : DEFAULT_MOBILE_PROFILES;

            Long orderId = null;
            BigDecimal paidPrice = null;
            if (sub.getOrderItemId() != null) {
                OrderItemEntity orderItem = orderItemsById.get(sub.getOrderItemId());
                if (orderItem != null) {
                    if (orderItem.getOrder() != null) {
                        orderId = orderItem.getOrder().getOrderId();
                    }
                    paidPrice = orderItem.getUnitPrice();
                }
            }
            if (paidPrice == null && product != null) {
                paidPrice = product.basePrice();
            }

            boolean isActiveStatus = STATUS_ACTIVE.equalsIgnoreCase(sub.getStatus());
            if (isActiveStatus && !expired) {
                activeList.add(new CustomerActiveSubscriptionResponse(
                        sub.getSubscriptionId(),
                        sub.getProductId(),
                        orderId,
                        detail != null ? detail.planCode() : null,
                        detail != null ? detail.planName() : null,
                        isLifetime,
                        durationDays,
                        allAccess,
                        maxCommissionRevisions,
                        maxCommissionRevisions,
                        maxMobileProfiles,
                        activatedAt,
                        expiresAt,
                        paidPrice
                ));
            } else {
                String resolvedStatus = (isActiveStatus && expired) ? STATUS_EXPIRED : sub.getStatus();
                historyList.add(new CustomerSubscriptionBenefitsResponse.SubscriptionHistoryItem(
                        sub.getSubscriptionId(),
                        sub.getProductId(),
                        orderId,
                        detail != null ? detail.planCode() : null,
                        detail != null ? detail.planName() : null,
                        isLifetime,
                        durationDays,
                        allAccess,
                        maxCommissionRevisions,
                        maxMobileProfiles,
                        resolvedStatus,
                        activatedAt,
                        expiresAt,
                        paidPrice
                ));
            }
        }

        boolean hasAllAccess = activeList.stream().anyMatch(CustomerActiveSubscriptionResponse::allAccessCharacters);
        int effectiveMobileProfiles = activeList.stream()
                .mapToInt(sub -> Math.max(DEFAULT_MOBILE_PROFILES, sub.maxMobileProfiles()))
                .max()
                .orElse(DEFAULT_MOBILE_PROFILES);
        int effectiveCommissionRevisions = activeList.stream()
                .mapToInt(sub -> Math.max(DEFAULT_COMMISSION_REVISIONS, sub.maxCommissionRevisions()))
                .max()
                .orElse(DEFAULT_COMMISSION_REVISIONS);
        boolean alreadyOwnsUpgrade = activeList.stream().anyMatch(this::isAllAccessUpgradePlan);
        BigDecimal upgradeCredit = alreadyOwnsUpgrade
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : activeList.stream()
                        .filter(this::isAllAccessOnlyPlan)
                        .map(CustomerActiveSubscriptionResponse::paidPrice)
                        .filter(Objects::nonNull)
                        .filter(price -> price.signum() > 0)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

        return new CustomerSubscriptionBenefitsResponse(
                !activeList.isEmpty(),
                hasAllAccess,
                DEFAULT_MOBILE_PROFILES,
                effectiveMobileProfiles,
                DEFAULT_COMMISSION_REVISIONS,
                effectiveCommissionRevisions,
                upgradeCredit,
                List.copyOf(activeList),
                List.copyOf(historyList)
        );
    }

    private CustomerSubscriptionBenefitsResponse emptyBenefits() {
        return new CustomerSubscriptionBenefitsResponse(
                false,
                false,
                DEFAULT_MOBILE_PROFILES,
                DEFAULT_MOBILE_PROFILES,
                DEFAULT_COMMISSION_REVISIONS,
                DEFAULT_COMMISSION_REVISIONS,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                List.of(),
                List.of()
        );
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
        Map<Long, ProductSnapshot> productsById =
                (productIds.isEmpty() || productLookupService == null)
                        ? Map.of()
                        : Optional.ofNullable(productLookupService.findSnapshotsByIds(productIds)).orElse(Map.of());

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
                .map(sub -> toResponseIfValid(
                        sub,
                        detailsByProductId.get(sub.getProductId()),
                        productsById.get(sub.getProductId()),
                        orderItemsById,
                        now))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<CustomerActiveSubscriptionResponse> toResponseIfValid(
            CustomerSubscriptionEntity sub,
            SubscriptionDetailSnapshot detail,
            ProductSnapshot product,
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
        int maxCommissionRevisions = detail != null ? Math.max(0, detail.maxCommissionRevisions()) : 0;
        int maxMobileProfiles = detail != null ? Math.max(DEFAULT_MOBILE_PROFILES, detail.maxMobileProfiles()) : DEFAULT_MOBILE_PROFILES;

        Long orderId = null;
        BigDecimal paidPrice = null;
        if (sub.getOrderItemId() != null) {
            OrderItemEntity orderItem = orderItemsById.get(sub.getOrderItemId());
            if (orderItem != null) {
                if (orderItem.getOrder() != null) {
                    orderId = orderItem.getOrder().getOrderId();
                }
                paidPrice = orderItem.getUnitPrice();
            }
        }
        if (paidPrice == null && product != null) {
            paidPrice = product.basePrice();
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
                maxCommissionRevisions,
                maxMobileProfiles,
                activatedAt,
                expiresAt,
                paidPrice
        ));
    }

    private boolean isAllAccessOnlyPlan(SubscriptionDetailSnapshot detail) {
        return detail != null
                && detail.allAccessCharacters()
                && detail.maxMobileProfiles() <= DEFAULT_MOBILE_PROFILES
                && detail.maxCommissionRevisions() <= 0;
    }

    private boolean isAllAccessOnlyPlan(CustomerActiveSubscriptionResponse sub) {
        return sub != null
                && sub.allAccessCharacters()
                && sub.maxMobileProfiles() <= DEFAULT_MOBILE_PROFILES
                && sub.maxCommissionRevisions() <= 0;
    }

    private boolean isAllAccessUpgradePlan(SubscriptionDetailSnapshot detail) {
        return detail != null
                && detail.allAccessCharacters()
                && (detail.maxMobileProfiles() > DEFAULT_MOBILE_PROFILES
                        || detail.maxCommissionRevisions() > 0);
    }

    private boolean isAllAccessUpgradePlan(CustomerActiveSubscriptionResponse sub) {
        return sub != null
                && sub.allAccessCharacters()
                && (sub.maxMobileProfiles() > DEFAULT_MOBILE_PROFILES
                        || sub.maxCommissionRevisions() > 0);
    }
}
