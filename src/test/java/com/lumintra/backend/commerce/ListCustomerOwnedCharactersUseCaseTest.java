package com.lumintra.backend.commerce;

import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.api.response.CustomerActiveSubscriptionResponse;
import com.lumintra.backend.commerce.api.response.CustomerOwnedCharacterResponse;
import com.lumintra.backend.commerce.application.CustomerSubscriptionEntitlementService;
import com.lumintra.backend.commerce.application.ListCustomerOwnedCharactersUseCase;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OwnedCharacterRepository;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListCustomerOwnedCharactersUseCaseTest {

    @Mock
    private OwnedCharacterRepository ownedCharacterRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CharacterLookupService characterLookupService;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private CustomerSubscriptionEntitlementService customerSubscriptionEntitlementService;

    @InjectMocks
    private ListCustomerOwnedCharactersUseCase listCustomerOwnedCharactersUseCase;

    private final Long customerId = 42L;

    @Test
    @DisplayName("execute: when customer has no owned characters, should return empty list")
    void execute_whenNoOwnedCharacters_shouldReturnEmptyList() {
        when(ownedCharacterRepository.findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(customerId))
                .thenReturn(Collections.emptyList());

        List<CustomerOwnedCharacterResponse> result = listCustomerOwnedCharactersUseCase.execute(customerId);

        assertThat(result).isEmpty();
        verify(ownedCharacterRepository).findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(customerId);
        verifyNoInteractions(characterLookupService, orderItemRepository, productLookupService);
    }

    @Test
    @DisplayName("execute: when customer owns characters with order items, should return enriched responses")
    void execute_whenOwnedCharactersWithOrderItems_shouldReturnEnrichedResponses() {
        Instant now = Instant.parse("2026-11-16T10:00:00Z");

        OwnedCharacterEntity owned1 = OwnedCharacterEntity.create(customerId, 12L, 101L);
        ReflectionTestUtils.setField(owned1, "ownedId", 1L);
        ReflectionTestUtils.setField(owned1, "grantedAt", now);

        OwnedCharacterEntity owned2 = OwnedCharacterEntity.create(customerId, 15L, 102L);
        ReflectionTestUtils.setField(owned2, "ownedId", 2L);
        ReflectionTestUtils.setField(owned2, "grantedAt", now.minusSeconds(3600));

        when(ownedCharacterRepository.findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(customerId))
                .thenReturn(List.of(owned1, owned2));

        CharacterVersionSnapshot version1 = new CharacterVersionSnapshot(
                12L, 5L, "Samurai Cat", "Cat animation", 1,
                "https://cdn.lumintra.shop/characters/5/preview.gif", 16, 60
        );
        CharacterVersionSnapshot version2 = new CharacterVersionSnapshot(
                15L, 8L, "Neon Dragon", "Dragon animation", 2,
                "https://cdn.lumintra.shop/characters/8/preview.gif", 24, 50
        );
        when(characterLookupService.findDetailsByVersionIds(Set.of(12L, 15L)))
                .thenReturn(Map.of(12L, version1, 15L, version2));

        OrderEntity order1 = OrderEntity.of(
                1001L, customerId, null, new BigDecimal("50000.00"),
                BigDecimal.ZERO, new BigDecimal("50000.00"),
                OrderType.DIGITAL, OrderStatus.COMPLETED, now.plusSeconds(3600)
        );

        OrderEntity order2 = OrderEntity.of(
                1002L, customerId, null, new BigDecimal("75000.00"),
                BigDecimal.ZERO, new BigDecimal("75000.00"),
                OrderType.DIGITAL, OrderStatus.COMPLETED, now.plusSeconds(3600)
        );

        OrderItemEntity item1 = OrderItemEntity.create(order1, 201L, 1, new BigDecimal("50000.00"));
        ReflectionTestUtils.setField(item1, "orderItemId", 101L);

        OrderItemEntity item2 = OrderItemEntity.create(order2, 202L, 1, new BigDecimal("75000.00"));
        ReflectionTestUtils.setField(item2, "orderItemId", 102L);

        when(orderItemRepository.findWithOrderByOrderItemIdIn(Set.of(101L, 102L)))
                .thenReturn(List.of(item1, item2));

        ProductSnapshot product1 = new ProductSnapshot(
                201L, "Samurai Cat Animated Edition", ProductType.CHARACTER,
                "https://cdn.lumintra.shop/thumb1.png", new BigDecimal("50000.00"), ProductStatus.PUBLISHED
        );
        ProductSnapshot product2 = new ProductSnapshot(
                202L, "Neon Dragon Pixel Pack", ProductType.CHARACTER,
                "https://cdn.lumintra.shop/thumb2.png", new BigDecimal("75000.00"), ProductStatus.PUBLISHED
        );
        when(productLookupService.findSnapshotsByIds(Set.of(201L, 202L)))
                .thenReturn(Map.of(201L, product1, 202L, product2));

        List<CustomerOwnedCharacterResponse> result = listCustomerOwnedCharactersUseCase.execute(customerId);

        assertThat(result).hasSize(2);

        CustomerOwnedCharacterResponse resp1 = result.get(0);
        assertThat(resp1.characterId()).isEqualTo(5L);
        assertThat(resp1.characterName()).isEqualTo("Samurai Cat");
        assertThat(resp1.previewGifUrl()).isEqualTo("https://cdn.lumintra.shop/characters/5/preview.gif");
        assertThat(resp1.frameCount()).isEqualTo(16);
        assertThat(resp1.frameDelayMs()).isEqualTo(60);
        assertThat(resp1.orderId()).isEqualTo(1001L);
        assertThat(resp1.storeProductId()).isEqualTo(201L);
        assertThat(resp1.productName()).isEqualTo("Samurai Cat Animated Edition");
        assertThat(resp1.purchasedPrice()).isEqualByComparingTo("50000.00");
        assertThat(resp1.purchasedAt()).isEqualTo(now);

        CustomerOwnedCharacterResponse resp2 = result.get(1);
        assertThat(resp2.characterId()).isEqualTo(8L);
        assertThat(resp2.characterName()).isEqualTo("Neon Dragon");
        assertThat(resp2.previewGifUrl()).isEqualTo("https://cdn.lumintra.shop/characters/8/preview.gif");
        assertThat(resp2.frameCount()).isEqualTo(24);
        assertThat(resp2.frameDelayMs()).isEqualTo(50);
        assertThat(resp2.orderId()).isEqualTo(1002L);
        assertThat(resp2.storeProductId()).isEqualTo(202L);
        assertThat(resp2.productName()).isEqualTo("Neon Dragon Pixel Pack");
        assertThat(resp2.purchasedPrice()).isEqualByComparingTo("75000.00");
    }

    @Test
    @DisplayName("execute: when owned character has null orderItemId, should fallback gracefully")
    void execute_whenOwnedCharacterWithoutOrderItem_shouldFallbackGracefully() {
        Instant now = Instant.parse("2026-11-16T11:00:00Z");

        OwnedCharacterEntity owned = OwnedCharacterEntity.create(customerId, 12L, null);
        ReflectionTestUtils.setField(owned, "ownedId", 3L);
        ReflectionTestUtils.setField(owned, "grantedAt", now);

        when(ownedCharacterRepository.findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(customerId))
                .thenReturn(List.of(owned));

        CharacterVersionSnapshot version = new CharacterVersionSnapshot(
                12L, 5L, "Gifted Cat", "Gifted animation", 1,
                "https://cdn.lumintra.shop/characters/5/gifted.gif", 10, 80
        );
        when(characterLookupService.findDetailsByVersionIds(Set.of(12L)))
                .thenReturn(Map.of(12L, version));

        List<CustomerOwnedCharacterResponse> result = listCustomerOwnedCharactersUseCase.execute(customerId);

        assertThat(result).hasSize(1);
        CustomerOwnedCharacterResponse resp = result.get(0);
        assertThat(resp.characterId()).isEqualTo(5L);
        assertThat(resp.characterName()).isEqualTo("Gifted Cat");
        assertThat(resp.previewGifUrl()).isEqualTo("https://cdn.lumintra.shop/characters/5/gifted.gif");
        assertThat(resp.orderId()).isNull();
        assertThat(resp.storeProductId()).isNull();
        assertThat(resp.purchasedPrice()).isNull();
        assertThat(resp.productName()).isEqualTo("Gifted Cat"); // Fallback to characterName
        assertThat(resp.purchasedAt()).isEqualTo(now);

        verifyNoInteractions(orderItemRepository, productLookupService);
    }

    @Test
    @DisplayName("execute: when character version snapshot not found, should skip gracefully")
    void execute_whenCharacterVersionNotFound_shouldSkipGracefully() {
        OwnedCharacterEntity owned = OwnedCharacterEntity.create(customerId, 999L, 101L);
        ReflectionTestUtils.setField(owned, "ownedId", 4L);
        ReflectionTestUtils.setField(owned, "grantedAt", Instant.now());

        when(ownedCharacterRepository.findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(customerId))
                .thenReturn(List.of(owned));

        when(characterLookupService.findDetailsByVersionIds(Set.of(999L)))
                .thenReturn(Collections.emptyMap());

        List<CustomerOwnedCharacterResponse> result = listCustomerOwnedCharactersUseCase.execute(customerId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("execute: when store product not found in product lookup, should fallback product name to character name")
    void execute_whenProductNotFound_shouldFallbackProductName() {
        Instant now = Instant.parse("2026-11-16T12:00:00Z");

        OwnedCharacterEntity owned = OwnedCharacterEntity.create(customerId, 12L, 101L);
        ReflectionTestUtils.setField(owned, "ownedId", 5L);
        ReflectionTestUtils.setField(owned, "grantedAt", now);

        when(ownedCharacterRepository.findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(customerId))
                .thenReturn(List.of(owned));

        CharacterVersionSnapshot version = new CharacterVersionSnapshot(
                12L, 5L, "Samurai Cat", "Cat animation", 1,
                "https://cdn.lumintra.shop/preview.gif", 16, 60
        );
        when(characterLookupService.findDetailsByVersionIds(Set.of(12L)))
                .thenReturn(Map.of(12L, version));

        OrderEntity order = OrderEntity.of(
                1001L, customerId, null, new BigDecimal("50000.00"),
                BigDecimal.ZERO, new BigDecimal("50000.00"),
                OrderType.DIGITAL, OrderStatus.COMPLETED, now.plusSeconds(3600)
        );

        OrderItemEntity item = OrderItemEntity.create(order, 999L, 1, new BigDecimal("50000.00"));
        ReflectionTestUtils.setField(item, "orderItemId", 101L);

        when(orderItemRepository.findWithOrderByOrderItemIdIn(Set.of(101L)))
                .thenReturn(List.of(item));

        when(productLookupService.findSnapshotsByIds(Set.of(999L)))
                .thenReturn(Collections.emptyMap());

        List<CustomerOwnedCharacterResponse> result = listCustomerOwnedCharactersUseCase.execute(customerId);

        assertThat(result).hasSize(1);
        CustomerOwnedCharacterResponse resp = result.get(0);
        assertThat(resp.productName()).isEqualTo("Samurai Cat");
        assertThat(resp.orderId()).isEqualTo(1001L);
        assertThat(resp.storeProductId()).isEqualTo(999L);
        assertThat(resp.purchasedPrice()).isEqualByComparingTo("50000.00");
    }

    @Test
    @DisplayName("execute: when customer holds active All-Access VIP Pass, includes all published characters without duplicating owned ones")
    void execute_whenActiveAllAccessSubscription_returnsAllPublishedCharactersWithoutDuplicates() {
        Instant now = Instant.parse("2026-11-24T06:00:00Z");

        OwnedCharacterEntity owned1 = OwnedCharacterEntity.create(customerId, 12L, null);
        ReflectionTestUtils.setField(owned1, "ownedId", 1L);
        ReflectionTestUtils.setField(owned1, "grantedAt", now.minusSeconds(7200));

        when(ownedCharacterRepository.findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(customerId))
                .thenReturn(List.of(owned1));

        CharacterVersionSnapshot versionOwned = new CharacterVersionSnapshot(
                12L, 5L, "Samurai Cat", "Cat animation", 1,
                "https://cdn.lumintra.shop/characters/5/preview.gif", 16, 60
        );
        CharacterVersionSnapshot versionUnlocked = new CharacterVersionSnapshot(
                15L, 8L, "Neon Dragon", "Dragon animation", 2,
                "https://cdn.lumintra.shop/characters/8/preview.gif", 24, 50
        );

        when(characterLookupService.findDetailsByVersionIds(Set.of(12L)))
                .thenReturn(Map.of(12L, versionOwned));

        CustomerActiveSubscriptionResponse vipSub = new CustomerActiveSubscriptionResponse(
                99L, 300L, 2001L, "VIP_LIFETIME", "Lumintra All-Access VIP Pass",
                true, null, true, 3, 10, now, null
        );
        when(customerSubscriptionEntitlementService.findActiveAllAccessSubscription(customerId))
                .thenReturn(java.util.Optional.of(vipSub));

        ProductSnapshot catProduct = new ProductSnapshot(
                201L, "Samurai Cat Store", ProductType.CHARACTER,
                "thumb1", new BigDecimal("50000.00"), ProductStatus.PUBLISHED
        );
        ProductSnapshot dragonProduct = new ProductSnapshot(
                202L, "Neon Dragon Store", ProductType.CHARACTER,
                "thumb2", new BigDecimal("75000.00"), ProductStatus.PUBLISHED
        );
        when(productLookupService.findPublishedByType(ProductType.CHARACTER))
                .thenReturn(List.of(catProduct, dragonProduct));
        when(characterLookupService.findDetailsByProductIds(Set.of(201L, 202L)))
                .thenReturn(Map.of(201L, versionOwned, 202L, versionUnlocked));

        List<CustomerOwnedCharacterResponse> result = listCustomerOwnedCharactersUseCase.execute(customerId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).characterId()).isEqualTo(5L);
        assertThat(result.get(0).unlockedBySubscription()).isFalse();

        assertThat(result.get(1).characterId()).isEqualTo(8L);
        assertThat(result.get(1).storeProductId()).isEqualTo(202L);
        assertThat(result.get(1).orderId()).isEqualTo(2001L);
        assertThat(result.get(1).productName()).isEqualTo("Lumintra All-Access VIP Pass");
        assertThat(result.get(1).purchasedPrice()).isEqualByComparingTo("0");
        assertThat(result.get(1).unlockedBySubscription()).isTrue();
    }
}
