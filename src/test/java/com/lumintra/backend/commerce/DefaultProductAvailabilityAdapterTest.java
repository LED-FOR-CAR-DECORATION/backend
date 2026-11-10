package com.lumintra.backend.commerce;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.infrastructure.integration.DefaultProductAvailabilityAdapter;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OwnedCharacterRepository;
import com.lumintra.backend.device.domain.service.LedStockLookupService;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.BundleSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.BundleLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class DefaultProductAvailabilityAdapterTest {

    @Mock
    private LedStockLookupService ledStockLookupService;

    @Mock
    private CharacterLookupService characterLookupService;

    @Mock
    private BundleLookupService bundleLookupService;

    @Mock
    private OwnedCharacterRepository ownedCharacterRepository;

    @Mock
    private CustomerSubscriptionRepository customerSubscriptionRepository;

    private DefaultProductAvailabilityAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultProductAvailabilityAdapter(
                ledStockLookupService,
                characterLookupService,
                bundleLookupService,
                ownedCharacterRepository,
                customerSubscriptionRepository
        );
    }

    @Test
    @DisplayName("hasSufficientLedStock checks stock >= requestedQuantity via LedStockLookupService")
    void hasSufficientLedStock_evaluatesBoundaryCorrectly() {
        when(ledStockLookupService.findStockByModelId(12L)).thenReturn(Optional.of(5L));

        assertThat(adapter.hasSufficientLedStock(12L, 5)).isTrue();
        assertThat(adapter.hasSufficientLedStock(12L, 6)).isFalse();
    }

    @Test
    @DisplayName("isDigitalProductOwned checks OWNED_CHARACTER and CUSTOMER_SUBSCRIPTION")
    void isDigitalProductOwned_checksCharacterAndSubscription() {
        when(characterLookupService.findVersionIdByProductId(30L)).thenReturn(Optional.of(300L));
        when(ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(42L, 300L)).thenReturn(true);
        when(customerSubscriptionRepository.existsByCustomerIdAndStatus(42L, "ACTIVE")).thenReturn(true);

        assertThat(adapter.isDigitalProductOwned(42L, 30L, ProductType.CHARACTER)).isTrue();
        assertThat(adapter.isDigitalProductOwned(42L, 31L, ProductType.SUBSCRIPTION)).isTrue();
    }

    @Test
    @DisplayName("evaluateBundleAvailability validates window, character ownership, and LED component stocks")
    void evaluateBundleAvailability_validatesAllBundleRules() {
        Instant now = Instant.now();
        BundleSnapshot activeBundle = new BundleSnapshot(
                9L, 70L, "Creator Pack", new BigDecimal("499000.00"), "PUBLISHED",
                now.minus(1, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS),
                List.of(
                        new BundleSnapshot.BundleItemSnapshot(1L, BundleItemType.LED, 12L, null, 2),
                        new BundleSnapshot.BundleItemSnapshot(2L, BundleItemType.CHARACTER, null, 300L, 1)
                )
        );

        when(bundleLookupService.findByProductId(70L)).thenReturn(Optional.of(activeBundle));
        when(ownedCharacterRepository.existsByCustomerIdAndVersionIdInAndIsRevokedFalse(42L, Set.of(300L)))
                .thenReturn(false);
        when(ledStockLookupService.findStocksByModelIds(Set.of(12L))).thenReturn(Map.of(12L, 4L));

        // 2 bundles * 2 LED units = 4 required -> exact match available
        AvailabilityCheckResult ok = adapter.evaluateBundleAvailability(42L, 70L, 2);
        assertThat(ok.available()).isTrue();

        // 3 bundles * 2 LED units = 6 required -> out of stock
        AvailabilityCheckResult outOfStock = adapter.evaluateBundleAvailability(42L, 70L, 3);
        assertThat(outOfStock.available()).isFalse();
        assertThat(outOfStock.unavailableReason()).isEqualTo(AvailabilityCheckResult.REASON_OUT_OF_STOCK);

        // Owned character in bundle
        when(ownedCharacterRepository.existsByCustomerIdAndVersionIdInAndIsRevokedFalse(42L, Set.of(300L)))
                .thenReturn(true);
        AvailabilityCheckResult owned = adapter.evaluateBundleAvailability(42L, 70L, 1);
        assertThat(owned.available()).isFalse();
        assertThat(owned.unavailableReason()).isEqualTo(AvailabilityCheckResult.REASON_ALREADY_OWNED);
    }
}
