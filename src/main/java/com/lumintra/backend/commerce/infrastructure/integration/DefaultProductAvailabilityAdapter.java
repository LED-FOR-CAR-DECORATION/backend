package com.lumintra.backend.commerce.infrastructure.integration;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.service.ProductAvailabilityPort;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CustomerSubscriptionRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OwnedCharacterRepository;
import com.lumintra.backend.device.domain.service.LedStockLookupService;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.BundleSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.BundleLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persistence-backed adapter implementing ProductAvailabilityPort by querying Device,
 * Character, Store (Bundle), and Commerce ownership read boundaries.
 */
@Component
@RequiredArgsConstructor
public class DefaultProductAvailabilityAdapter implements ProductAvailabilityPort {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final LedStockLookupService ledStockLookupService;
    private final CharacterLookupService characterLookupService;
    private final BundleLookupService bundleLookupService;
    private final OwnedCharacterRepository ownedCharacterRepository;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;

    @Override
    public boolean hasSufficientLedStock(Long productId, int requestedQuantity) {
        if (productId == null || requestedQuantity <= 0) {
            return false;
        }
        return ledStockLookupService.findStockByModelId(productId)
                .map(stock -> stock >= requestedQuantity)
                .orElse(false);
    }

    @Override
    public boolean isDigitalProductOwned(Long customerId, Long productId, ProductType productType) {
        if (customerId == null || productId == null || productType == null) {
            return false;
        }

        if (productType == ProductType.CHARACTER) {
            return characterLookupService.findVersionIdByProductId(productId)
                    .map(versionId -> ownedCharacterRepository.existsByCustomerIdAndVersionIdAndIsRevokedFalse(customerId, versionId))
                    .orElse(false);
        }

        if (productType == ProductType.SUBSCRIPTION) {
            return customerSubscriptionRepository.existsByCustomerIdAndStatus(customerId, STATUS_ACTIVE);
        }

        return false;
    }

    @Override
    public AvailabilityCheckResult evaluateBundleAvailability(Long customerId, Long productId, int requestedQuantity) {
        if (productId == null || requestedQuantity <= 0) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE);
        }

        Optional<BundleSnapshot> bundleOpt = bundleLookupService.findByProductId(productId);
        if (bundleOpt.isEmpty()) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE);
        }

        BundleSnapshot bundle = bundleOpt.get();
        if (bundle.status() != null
                && !STATUS_PUBLISHED.equalsIgnoreCase(bundle.status())
                && !STATUS_ACTIVE.equalsIgnoreCase(bundle.status())) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE);
        }

        Instant now = Instant.now();
        if ((bundle.startAt() != null && now.isBefore(bundle.startAt()))
                || (bundle.endAt() != null && now.isAfter(bundle.endAt()))) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE);
        }

        Set<Long> versionIds = bundle.items().stream()
                .filter(item -> item.itemType() == BundleItemType.CHARACTER && item.versionId() != null)
                .map(BundleSnapshot.BundleItemSnapshot::versionId)
                .collect(Collectors.toSet());

        if (!versionIds.isEmpty()
                && customerId != null
                && ownedCharacterRepository.existsByCustomerIdAndVersionIdInAndIsRevokedFalse(customerId, versionIds)) {
            return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_ALREADY_OWNED);
        }

        Map<Long, Long> requiredStockByModelId = new HashMap<>();
        for (BundleSnapshot.BundleItemSnapshot item : bundle.items()) {
            if (item.itemType() == BundleItemType.LED && item.modelId() != null) {
                long required = (long) item.quantity() * requestedQuantity;
                requiredStockByModelId.merge(item.modelId(), required, Long::sum);
            }
        }

        if (!requiredStockByModelId.isEmpty()) {
            Map<Long, Long> availableStocks = ledStockLookupService.findStocksByModelIds(requiredStockByModelId.keySet());
            for (Map.Entry<Long, Long> entry : requiredStockByModelId.entrySet()) {
                long available = availableStocks.getOrDefault(entry.getKey(), 0L);
                if (available < entry.getValue()) {
                    return AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_OUT_OF_STOCK);
                }
            }
        }

        return AvailabilityCheckResult.availableResult();
    }
}
