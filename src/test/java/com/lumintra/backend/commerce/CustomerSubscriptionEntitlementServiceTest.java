package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.response.CustomerActiveSubscriptionResponse;
import com.lumintra.backend.commerce.application.CustomerSubscriptionEntitlementService;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.model.SubscriptionDetailSnapshot;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import com.lumintra.backend.store.domain.service.SubscriptionLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSubscriptionEntitlementServiceTest {

    @Mock
    private CustomerSubscriptionRepository customerSubscriptionRepository;

    @Mock
    private SubscriptionLookupService subscriptionLookupService;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    @InjectMocks
    private CustomerSubscriptionEntitlementService service;

    @Test
    @DisplayName("Lifetime VIP subscription grants all-access characters entitlement")
    void lifetimeAllAccessPlan_grantsAllAccessCharacters() {
        CustomerSubscriptionEntity entity = CustomerSubscriptionEntity.createActive(42L, 16L, null);
        ReflectionTestUtils.setField(entity, "subscriptionId", 1L);
        ReflectionTestUtils.setField(entity, "activatedAt", Instant.now().minus(5, ChronoUnit.DAYS));

        when(customerSubscriptionRepository.findByCustomerIdAndStatusOrderByActivatedAtDesc(42L, "ACTIVE"))
                .thenReturn(List.of(entity));
        when(subscriptionLookupService.findByProductIds(Set.of(16L)))
                .thenReturn(Map.of(16L, new SubscriptionDetailSnapshot(
                        16L, "VIP_LIFETIME", "Lumintra All-Access VIP Pass",
                        true, null, true, 3, 10
                )));

        assertThat(service.hasActiveAllAccessCharacters(42L)).isTrue();
        Optional<CustomerActiveSubscriptionResponse> active = service.findActiveSubscription(42L);
        assertThat(active).isPresent();
        assertThat(active.get().allAccessCharacters()).isTrue();
        assertThat(active.get().planCode()).isEqualTo("VIP_LIFETIME");
    }

    @Test
    @DisplayName("Commission-only plan or expired time-limited plan does not grant all-access characters")
    void commissionOnlyOrExpiredPlan_doesNotGrantAllAccess() {
        CustomerSubscriptionEntity commissionSub = CustomerSubscriptionEntity.createActive(42L, 17L, null, 5);
        ReflectionTestUtils.setField(commissionSub, "subscriptionId", 2L);
        ReflectionTestUtils.setField(commissionSub, "activatedAt", Instant.now().minus(2, ChronoUnit.DAYS));

        when(customerSubscriptionRepository.findByCustomerIdAndStatusOrderByActivatedAtDesc(42L, "ACTIVE"))
                .thenReturn(List.of(commissionSub));
        when(subscriptionLookupService.findByProductIds(Set.of(17L)))
                .thenReturn(Map.of(17L, new SubscriptionDetailSnapshot(
                        17L, "VIP_COMMISSION", "Lumintra Commission VIP Pass",
                        false, 30, false, 5, 5
                )));

        assertThat(service.hasActiveAllAccessCharacters(42L)).isFalse();
        assertThat(service.findActiveSubscription(42L)).isPresent();
        assertThat(service.findActiveSubscription(42L).get().allAccessCharacters()).isFalse();

        // Expired 30-day all-access plan should not be returned as active
        CustomerSubscriptionEntity expiredAllAccess = CustomerSubscriptionEntity.createActive(43L, 18L, null);
        ReflectionTestUtils.setField(expiredAllAccess, "subscriptionId", 3L);
        ReflectionTestUtils.setField(expiredAllAccess, "activatedAt", Instant.now().minus(40, ChronoUnit.DAYS));

        when(customerSubscriptionRepository.findByCustomerIdAndStatusOrderByActivatedAtDesc(43L, "ACTIVE"))
                .thenReturn(List.of(expiredAllAccess));
        when(subscriptionLookupService.findByProductIds(Set.of(18L)))
                .thenReturn(Map.of(18L, new SubscriptionDetailSnapshot(
                        18L, "VIP_30D", "30-Day All-Access",
                        false, 30, true, 1, 5
                )));

        assertThat(service.hasActiveAllAccessCharacters(43L)).isFalse();
        assertThat(service.findActiveSubscription(43L)).isEmpty();
    }

    @Test
    @DisplayName("Tier-based subscriptions use highest active allowance, allow Ultimate upgrade, and deduct Full-Char price")
    void tierBasedPlans_useHighestAllowanceAndDeductAllAccessOnlyPriceOnUltimateUpgrade() {
        CustomerSubscriptionEntity fullCharOnly = CustomerSubscriptionEntity.createActive(42L, 16L, null, 0);
        ReflectionTestUtils.setField(fullCharOnly, "subscriptionId", 10L);
        ReflectionTestUtils.setField(fullCharOnly, "activatedAt", Instant.now().minus(5, ChronoUnit.DAYS));

        CustomerSubscriptionEntity standard30d = CustomerSubscriptionEntity.createActive(42L, 17L, null, 3);
        ReflectionTestUtils.setField(standard30d, "subscriptionId", 11L);
        ReflectionTestUtils.setField(standard30d, "activatedAt", Instant.now().minus(2, ChronoUnit.DAYS));

        CustomerSubscriptionEntity pro30d = CustomerSubscriptionEntity.createActive(42L, 18L, null, 5);
        ReflectionTestUtils.setField(pro30d, "subscriptionId", 12L);
        ReflectionTestUtils.setField(pro30d, "activatedAt", Instant.now().minus(1, ChronoUnit.DAYS));

        when(customerSubscriptionRepository.findByCustomerIdAndStatusOrderByActivatedAtDesc(42L, "ACTIVE"))
                .thenReturn(List.of(pro30d, standard30d, fullCharOnly));
        when(subscriptionLookupService.findByProductIds(Set.of(16L, 17L, 18L)))
                .thenReturn(Map.of(
                        16L, new SubscriptionDetailSnapshot(16L, "VIP_LIFETIME", "All Access", true, null, true, 0, 3),
                        17L, new SubscriptionDetailSnapshot(17L, "VIP_STANDARD_30D", "Standard 30D", false, 30, false, 3, 5),
                        18L, new SubscriptionDetailSnapshot(18L, "VIP_PRO_30D", "Pro 30D", false, 30, false, 5, 10)
                ));
        when(productLookupService.findSnapshotsByIds(Set.of(16L, 17L, 18L)))
                .thenReturn(Map.of(
                        16L, new ProductSnapshot(16L, "All Access", ProductType.SUBSCRIPTION, null, new BigDecimal("999999.00"), ProductStatus.PUBLISHED),
                        17L, new ProductSnapshot(17L, "Standard 30D", ProductType.SUBSCRIPTION, null, new BigDecimal("399999.00"), ProductStatus.PUBLISHED),
                        18L, new ProductSnapshot(18L, "Pro 30D", ProductType.SUBSCRIPTION, null, new BigDecimal("599999.00"), ProductStatus.PUBLISHED)
                ));
        when(subscriptionLookupService.findByProductIds(Set.of(19L)))
                .thenReturn(Map.of(
                        19L, new SubscriptionDetailSnapshot(19L, "VIP_ULTIMATE_LIFETIME", "Ultimate Lifetime", true, null, true, 10, 20)
                ));

        assertThat(service.findActiveSubscriptions(42L)).hasSize(3);
        assertThat(service.hasActiveAllAccessCharacters(42L)).isTrue();
        // Tier-based max: 5 free revisions per commission, 10 mobile profiles
        assertThat(service.findMaxFreeCommissionRevisions(42L)).isEqualTo(5);
        assertThat(service.findMaxMobileProfiles(42L)).isEqualTo(10);

        // Already-owned plans are blocked; Ultimate upgrade (19L) is allowed
        assertThat(service.isSubscriptionProductAlreadyOwned(42L, 16L)).isTrue();
        assertThat(service.isSubscriptionProductAlreadyOwned(42L, 17L)).isTrue();
        assertThat(service.isSubscriptionProductAlreadyOwned(42L, 19L)).isFalse();

        // Ultimate price 4,999,999 - Full-Char-Only price 999,999 = 4,000,000
        ProductSnapshot ultimateProduct = new ProductSnapshot(
                19L, "Ultimate Lifetime", ProductType.SUBSCRIPTION, null,
                new BigDecimal("4999999.00"), ProductStatus.PUBLISHED
        );
        assertThat(service.resolveEffectiveSubscriptionPrice(42L, ultimateProduct))
                .isEqualByComparingTo("4000000.00");
    }

    @Test
    @DisplayName("getCustomerSubscriptionBenefits aggregates effective limits, upgrade credit, active plans, and expired history")
    void getCustomerSubscriptionBenefits_returnsAggregatedBenefitsAndHistory() {
        CustomerSubscriptionEntity activeFullChar = CustomerSubscriptionEntity.createActive(42L, 16L, null, 0);
        ReflectionTestUtils.setField(activeFullChar, "subscriptionId", 20L);
        ReflectionTestUtils.setField(activeFullChar, "activatedAt", Instant.now().minus(10, ChronoUnit.DAYS));

        CustomerSubscriptionEntity activePro = CustomerSubscriptionEntity.createActive(42L, 18L, null, 5);
        activePro.setLifetime(false);
        ReflectionTestUtils.setField(activePro, "subscriptionId", 21L);
        ReflectionTestUtils.setField(activePro, "activatedAt", Instant.now().minus(5, ChronoUnit.DAYS));

        CustomerSubscriptionEntity expiredStandard = CustomerSubscriptionEntity.createActive(42L, 17L, null, 3);
        expiredStandard.setLifetime(false);
        ReflectionTestUtils.setField(expiredStandard, "subscriptionId", 22L);
        ReflectionTestUtils.setField(expiredStandard, "activatedAt", Instant.now().minus(45, ChronoUnit.DAYS));

        when(customerSubscriptionRepository.findByCustomerIdOrderByActivatedAtDesc(42L))
                .thenReturn(List.of(activePro, activeFullChar, expiredStandard));
        when(subscriptionLookupService.findByProductIds(Set.of(16L, 17L, 18L)))
                .thenReturn(Map.of(
                        16L, new SubscriptionDetailSnapshot(16L, "VIP_LIFETIME", "All Access", true, null, true, 0, 3),
                        17L, new SubscriptionDetailSnapshot(17L, "VIP_STANDARD_30D", "Standard 30D", false, 30, false, 3, 5),
                        18L, new SubscriptionDetailSnapshot(18L, "VIP_PRO_30D", "Pro 30D", false, 30, false, 5, 10)
                ));
        when(productLookupService.findSnapshotsByIds(Set.of(16L, 17L, 18L)))
                .thenReturn(Map.of(
                        16L, new ProductSnapshot(16L, "All Access", ProductType.SUBSCRIPTION, null, new BigDecimal("999999.00"), ProductStatus.PUBLISHED),
                        17L, new ProductSnapshot(17L, "Standard 30D", ProductType.SUBSCRIPTION, null, new BigDecimal("399999.00"), ProductStatus.PUBLISHED),
                        18L, new ProductSnapshot(18L, "Pro 30D", ProductType.SUBSCRIPTION, null, new BigDecimal("599999.00"), ProductStatus.PUBLISHED)
                ));

        var benefits = service.getCustomerSubscriptionBenefits(42L);

        assertThat(benefits.hasActiveSubscription()).isTrue();
        assertThat(benefits.allAccessCharacters()).isTrue();
        assertThat(benefits.defaultMobileProfiles()).isEqualTo(3);
        assertThat(benefits.maxMobileProfiles()).isEqualTo(10);
        assertThat(benefits.defaultCommissionRevisions()).isEqualTo(1);
        assertThat(benefits.maxCommissionRevisions()).isEqualTo(5);
        assertThat(benefits.upgradeCreditAvailable()).isEqualByComparingTo("999999.00");
        assertThat(benefits.activeSubscriptions()).hasSize(2);
        assertThat(benefits.subscriptionHistory()).hasSize(1);
        assertThat(benefits.subscriptionHistory().getFirst().status()).isEqualTo("EXPIRED");
        assertThat(benefits.subscriptionHistory().getFirst().planCode()).isEqualTo("VIP_STANDARD_30D");
    }
}
