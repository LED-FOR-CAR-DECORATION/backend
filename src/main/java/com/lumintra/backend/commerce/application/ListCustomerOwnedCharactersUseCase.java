package com.lumintra.backend.commerce.application;

import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.api.response.CustomerActiveSubscriptionResponse;
import com.lumintra.backend.commerce.api.response.CustomerOwnedCharacterResponse;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OwnedCharacterRepository;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListCustomerOwnedCharactersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListCustomerOwnedCharactersUseCase.class);

    private final OwnedCharacterRepository ownedCharacterRepository;
    private final OrderItemRepository orderItemRepository;
    private final CharacterLookupService characterLookupService;
    private final ProductLookupService productLookupService;
    private final CustomerSubscriptionEntitlementService customerSubscriptionEntitlementService;

    @Transactional(readOnly = true)
    public List<CustomerOwnedCharacterResponse> execute(Long customerId) {
        log.info("Fetching owned characters for customer [customerId={}]", customerId);

        List<OwnedCharacterEntity> ownedEntities = ownedCharacterRepository
                .findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(customerId);
        Optional<CustomerActiveSubscriptionResponse> activeAllAccess =
                customerSubscriptionEntitlementService.findActiveAllAccessSubscription(customerId);

        if (ownedEntities.isEmpty() && activeAllAccess.isEmpty()) {
            return Collections.emptyList();
        }

        List<CustomerOwnedCharacterResponse> result = new ArrayList<>();
        Set<Long> includedCharacterIds = new HashSet<>();

        if (!ownedEntities.isEmpty()) {
            // Collect version IDs
            Set<Long> versionIds = ownedEntities.stream()
                    .map(OwnedCharacterEntity::getVersionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Batch fetch character version details via CharacterLookupService
            Map<Long, CharacterVersionSnapshot> versionDetails = characterLookupService.findDetailsByVersionIds(versionIds);

            // Collect non-null order item IDs
            Set<Long> orderItemIds = ownedEntities.stream()
                    .map(OwnedCharacterEntity::getOrderItemId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Batch fetch OrderItems with Order
            Map<Long, OrderItemEntity> orderItemMap = orderItemIds.isEmpty()
                    ? Collections.emptyMap()
                    : orderItemRepository.findWithOrderByOrderItemIdIn(orderItemIds).stream()
                    .collect(Collectors.toMap(OrderItemEntity::getOrderItemId, Function.identity(), (a, b) -> a));

            // Collect product IDs from OrderItems
            Set<Long> productIds = orderItemMap.values().stream()
                    .map(OrderItemEntity::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Batch fetch product snapshots via ProductLookupService
            Map<Long, ProductSnapshot> productMap = productIds.isEmpty()
                    ? Collections.emptyMap()
                    : productLookupService.findSnapshotsByIds(productIds);

            for (OwnedCharacterEntity owned : ownedEntities) {
                CharacterVersionSnapshot version = versionDetails.get(owned.getVersionId());
                if (version == null) {
                    log.warn("Character version not found for owned record [ownedId={}] [versionId={}]",
                            owned.getOwnedId(), owned.getVersionId());
                    continue;
                }

                Long orderId = null;
                Long storeProductId = null;
                String productName = null;
                BigDecimal purchasedPrice = null;

                if (owned.getOrderItemId() != null) {
                    OrderItemEntity orderItem = orderItemMap.get(owned.getOrderItemId());
                    if (orderItem != null) {
                        orderId = orderItem.getOrder() != null ? orderItem.getOrder().getOrderId() : null;
                        storeProductId = orderItem.getProductId();
                        purchasedPrice = orderItem.getUnitPrice();

                        ProductSnapshot product = productMap.get(storeProductId);
                        if (product != null) {
                            productName = product.productName();
                        }
                    }
                }

                // Fallback product name to character name if not bought from store or not found
                if (productName == null) {
                    productName = version.characterName();
                }

                if (version.characterId() != null) {
                    includedCharacterIds.add(version.characterId());
                }

                result.add(new CustomerOwnedCharacterResponse(
                        version.characterId(),
                        version.characterName(),
                        version.previewGifUrl(),
                        version.frameCount(),
                        version.frameDelayMs(),
                        orderId,
                        storeProductId,
                        productName,
                        purchasedPrice,
                        owned.getGrantedAt(),
                        false
                ));
            }
        }

        if (activeAllAccess.isPresent()) {
            CustomerActiveSubscriptionResponse subscription = activeAllAccess.get();
            List<ProductSnapshot> publishedCharacters = productLookupService.findPublishedByType(ProductType.CHARACTER);
            Set<Long> catalogProductIds = publishedCharacters.stream()
                    .map(ProductSnapshot::productId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, CharacterVersionSnapshot> versionsByProductId =
                    catalogProductIds.isEmpty() ? Map.of() : characterLookupService.findDetailsByProductIds(catalogProductIds);

            String sourcePlanName = subscription.planName() != null && !subscription.planName().isBlank()
                    ? subscription.planName()
                    : "All-Access VIP Pass";

            for (ProductSnapshot product : publishedCharacters) {
                CharacterVersionSnapshot version = versionsByProductId.get(product.productId());
                if (version == null || version.characterId() == null) {
                    continue;
                }
                if (!includedCharacterIds.add(version.characterId())) {
                    continue;
                }
                result.add(new CustomerOwnedCharacterResponse(
                        version.characterId(),
                        version.characterName(),
                        version.previewGifUrl(),
                        version.frameCount(),
                        version.frameDelayMs(),
                        subscription.orderId(),
                        product.productId(),
                        sourcePlanName,
                        BigDecimal.ZERO,
                        subscription.activatedAt(),
                        true
                ));
            }
        }

        log.info("Returned owned characters for customer [customerId={}] [count={}] [allAccess={}]",
                customerId, result.size(), activeAllAccess.isPresent());
        return result;
    }
}
