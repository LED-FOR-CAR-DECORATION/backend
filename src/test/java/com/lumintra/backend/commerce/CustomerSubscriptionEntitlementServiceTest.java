package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.response.CustomerActiveSubscriptionResponse;
import com.lumintra.backend.commerce.application.CustomerSubscriptionEntitlementService;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.store.domain.model.SubscriptionDetailSnapshot;
import com.lumintra.backend.store.domain.service.SubscriptionLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("Multiple active lifetime plans sum remaining commission credits, allow rebuying credit packs, and consume FIFO")
    void multipleActivePlans_sumCommissionRevisionsAndConsumeFifo() {
        CustomerSubscriptionEntity allAccess = CustomerSubscriptionEntity.createActive(42L, 16L, null, 0);
        ReflectionTestUtils.setField(allAccess, "subscriptionId", 10L);
        ReflectionTestUtils.setField(allAccess, "activatedAt", Instant.now().minus(5, ChronoUnit.DAYS));

        CustomerSubscriptionEntity bonus5 = CustomerSubscriptionEntity.createActive(42L, 17L, null, 1);
        ReflectionTestUtils.setField(bonus5, "subscriptionId", 11L);
        ReflectionTestUtils.setField(bonus5, "activatedAt", Instant.now().minus(2, ChronoUnit.DAYS));

        CustomerSubscriptionEntity bonus10 = CustomerSubscriptionEntity.createActive(42L, 18L, null, 10);
        ReflectionTestUtils.setField(bonus10, "subscriptionId", 12L);
        ReflectionTestUtils.setField(bonus10, "activatedAt", Instant.now().minus(1, ChronoUnit.DAYS));

        when(customerSubscriptionRepository.findByCustomerIdAndStatusOrderByActivatedAtDesc(42L, "ACTIVE"))
                .thenReturn(List.of(bonus10, bonus5, allAccess));
        when(subscriptionLookupService.findByProductIds(Set.of(16L, 17L, 18L)))
                .thenReturn(Map.of(
                        16L, new SubscriptionDetailSnapshot(16L, "VIP_LIFETIME", "All Access", true, null, true, 0, 10),
                        17L, new SubscriptionDetailSnapshot(17L, "VIP_COMMISSION", "Commission +5", true, null, false, 5, 5),
                        18L, new SubscriptionDetailSnapshot(18L, "VIP_COMMISSION_10", "Commission +10", true, null, false, 10, 5)
                ));
        when(subscriptionLookupService.findByProductIds(Set.of(16L)))
                .thenReturn(Map.of(16L, new SubscriptionDetailSnapshot(16L, "VIP_LIFETIME", "All Access", true, null, true, 0, 10)));
        when(subscriptionLookupService.findByProductIds(Set.of(17L)))
                .thenReturn(Map.of(17L, new SubscriptionDetailSnapshot(17L, "VIP_COMMISSION", "Commission +5", true, null, false, 5, 5)));

        assertThat(service.findActiveSubscriptions(42L)).hasSize(3);
        assertThat(service.hasActiveAllAccessCharacters(42L)).isTrue();
        assertThat(service.findExtraCommissionRevisions(42L)).isEqualTo(11);
        // All-Access is owned; repeatable credit pack is NOT blocked from rebuy
        assertThat(service.isSubscriptionProductAlreadyOwned(42L, 16L)).isTrue();
        assertThat(service.isSubscriptionProductAlreadyOwned(42L, 17L)).isFalse();

        // Consuming 1 credit depletes bonus5 (1 -> 0) and marks it EXHAUSTED
        when(customerSubscriptionRepository.findActiveWithRemainingRevisionsForUpdate(42L, "ACTIVE"))
                .thenReturn(List.of(bonus5, bonus10));
        when(subscriptionLookupService.findByProductIds(Set.of(17L, 18L)))
                .thenReturn(Map.of(
                        17L, new SubscriptionDetailSnapshot(17L, "VIP_COMMISSION", "Commission +5", true, null, false, 5, 5),
                        18L, new SubscriptionDetailSnapshot(18L, "VIP_COMMISSION_10", "Commission +10", true, null, false, 10, 5)
                ));

        assertThat(service.consumeOneExtraCommissionRevision(42L)).isTrue();
        assertThat(bonus5.getRemainingCommissionRevisions()).isZero();
        assertThat(bonus5.getStatus()).isEqualTo("EXHAUSTED");
        assertThat(bonus10.getRemainingCommissionRevisions()).isEqualTo(10);
    }
}
