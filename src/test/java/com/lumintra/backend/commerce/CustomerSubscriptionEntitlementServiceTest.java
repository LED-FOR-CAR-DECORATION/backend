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
        CustomerSubscriptionEntity commissionSub = CustomerSubscriptionEntity.createActive(42L, 17L, null);
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
}
