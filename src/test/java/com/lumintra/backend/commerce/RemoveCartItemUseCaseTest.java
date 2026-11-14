package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.RemoveCartItemUseCase;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.service.CartItemAvailabilityService;
import com.lumintra.backend.commerce.domain.service.ProductAvailabilityPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.CartMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.shared.exception.commerce.CartItemNotFoundException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveCartItemUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private ProductAvailabilityPort productAvailabilityPort;

    private RemoveCartItemUseCase removeCartItemUseCase;

    @BeforeEach
    void setUp() {
        CartItemAvailabilityService availabilityService = new CartItemAvailabilityService(productAvailabilityPort);
        CartMapper cartMapper = new CartMapper();
        removeCartItemUseCase = new RemoveCartItemUseCase(
                cartRepository,
                cartItemRepository,
                productLookupService,
                availabilityService,
                cartMapper
        );
    }

    @Test
    @DisplayName("UTCID01 [N]: AC03, AC05 & AC06 deletes owned item, flushes before reload, touches cart, and returns remaining items")
    void execute_ownedItemWithRemainingItems_deletesFlushesTouchesCartAndReturnsRemainingCart() {
        Long customerId = 42L;
        Instant oldUpdatedAt = Instant.parse("2026-11-10T02:00:00Z");
        Instant remainingAddedAt = Instant.parse("2026-11-10T02:30:00Z");

        CartEntity cart = CartEntity.of(101L, customerId, oldUpdatedAt);
        CartItemEntity removedItem = CartItemEntity.of(
                501L, cart, 12L, 2, new BigDecimal("350000.00"), Instant.parse("2026-11-10T02:15:00Z")
        );
        CartItemEntity remainingItem = CartItemEntity.of(
                502L, cart, 30L, 1, new BigDecimal("59000.00"), remainingAddedAt
        );
        ProductSnapshot characterSnapshot = new ProductSnapshot(
                30L, "Cyber Mascot", ProductType.CHARACTER, "https://img.lumintra.shop/mascot.png",
                new BigDecimal("65000.00"), ProductStatus.PUBLISHED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(501L, customerId)).thenReturn(Optional.of(removedItem));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(remainingItem));
        when(productLookupService.findSnapshotsByIds(Set.of(30L))).thenReturn(Map.of(30L, characterSnapshot));
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 30L, ProductType.CHARACTER)).thenReturn(false);

        CartResponse response = removeCartItemUseCase.execute(customerId, 501L);

        assertThat(cart.getUpdatedAt()).isAfter(oldUpdatedAt);
        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.updatedAt()).isEqualTo(cart.getUpdatedAt());
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).cartItemId()).isEqualTo(502L);
        assertThat(response.items().get(0).productId()).isEqualTo(30L);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("65000.00");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("65000.00");
        assertThat(response.items().get(0).isAvailable()).isTrue();
        assertThat(response.items().get(0).unavailableReason()).isNull();

        InOrder inOrder = inOrder(cartItemRepository, cartRepository);
        inOrder.verify(cartItemRepository).delete(removedItem);
        inOrder.verify(cartItemRepository).flush();
        inOrder.verify(cartRepository).save(cart);
        inOrder.verify(cartItemRepository).findAllByCart_CartIdOrderByAddedAtDesc(101L);
    }

    @Test
    @DisplayName("UTCID02 [B]: AC05 & AC06 removing last item retains empty cart, updates updatedAt, and skips product batch lookup")
    void execute_lastItemInCart_deletesFlushesRetainsEmptyCartAndSkipsProductLookup() {
        Long customerId = 42L;
        Instant oldUpdatedAt = Instant.parse("2026-11-10T02:00:00Z");

        CartEntity cart = CartEntity.of(101L, customerId, oldUpdatedAt);
        CartItemEntity onlyItem = CartItemEntity.of(
                501L, cart, 12L, 1, new BigDecimal("350000.00"), Instant.parse("2026-11-10T02:15:00Z")
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(501L, customerId)).thenReturn(Optional.of(onlyItem));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of());

        CartResponse response = removeCartItemUseCase.execute(customerId, 501L);

        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.items()).isEmpty();
        assertThat(response.updatedAt()).isAfter(oldUpdatedAt);

        verify(cartItemRepository).delete(onlyItem);
        verify(cartItemRepository).flush();
        verify(cartRepository).save(cart);
        verify(cartRepository, never()).delete(any());
        verifyNoInteractions(productLookupService, productAvailabilityPort);
    }

    @Test
    @DisplayName("UTCID03 [A]: AC03 throws CartItemNotFoundException when itemId is missing, deleted, or owned by another customer")
    void execute_missingOrUnownedCartItem_throwsCartItemNotFoundWithoutCreatingOrDeleting() {
        Long customerId = 42L;
        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(999L, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> removeCartItemUseCase.execute(customerId, 999L))
                .isInstanceOf(CartItemNotFoundException.class);

        verify(cartItemRepository, never()).delete(any());
        verify(cartItemRepository, never()).flush();
        verifyNoInteractions(cartRepository, productLookupService, productAvailabilityPort);
    }

    @Test
    @DisplayName("UTCID04 [N]: AC04 removes digital, archived, unpublished, out-of-stock, or owned items unconditionally")
    void execute_digitalOrUnavailableRemovedItem_deletesWithoutCheckingProductOrStock() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");

        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity unavailableDigitalItem = CartItemEntity.of(
                503L, cart, 999L, 1, new BigDecimal("99000.00"), now
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(503L, customerId))
                .thenReturn(Optional.of(unavailableDigitalItem));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of());

        CartResponse response = removeCartItemUseCase.execute(customerId, 503L);

        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.items()).isEmpty();
        verify(cartItemRepository).delete(unavailableDigitalItem);
        verify(cartItemRepository).flush();
        verifyNoInteractions(productLookupService, productAvailabilityPort);
    }

    @Test
    @DisplayName("UTCID05 [N]: AC06 evaluates live unavailability reasons cleanly for remaining items after deletion")
    void execute_remainingCartContainsUnavailableItems_mapsUnavailableReasonsInResponse() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");

        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity itemToDelete = CartItemEntity.of(501L, cart, 12L, 1, new BigDecimal("350000.00"), now);
        CartItemEntity archivedRemaining = CartItemEntity.of(502L, cart, 15L, 2, new BigDecimal("200000.00"), now);
        ProductSnapshot archivedLed = new ProductSnapshot(
                15L, "Discontinued LED", ProductType.LED, null,
                new BigDecimal("200000.00"), ProductStatus.ARCHIVED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(501L, customerId))
                .thenReturn(Optional.of(itemToDelete));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(archivedRemaining));
        when(productLookupService.findSnapshotsByIds(Set.of(15L))).thenReturn(Map.of(15L, archivedLed));

        CartResponse response = removeCartItemUseCase.execute(customerId, 501L);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).cartItemId()).isEqualTo(502L);
        assertThat(response.items().get(0).isAvailable()).isFalse();
        assertThat(response.items().get(0).unavailableReason()).isEqualTo(AvailabilityCheckResult.REASON_PRODUCT_ARCHIVED);
    }

    @Test
    @DisplayName("UTCID06 [A]: AC05 propagates unexpected persistence exceptions on delete without touching parent cart")
    void execute_persistenceDeleteFailure_propagatesExceptionWithoutSavingCart() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");

        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity item = CartItemEntity.of(501L, cart, 12L, 1, new BigDecimal("350000.00"), now);

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(501L, customerId)).thenReturn(Optional.of(item));
        doThrow(new RuntimeException("DB lock timeout")).when(cartItemRepository).flush();

        assertThatThrownBy(() -> removeCartItemUseCase.execute(customerId, 501L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB lock timeout");

        verify(cartItemRepository).delete(item);
        verify(cartRepository, never()).save(any());
        verifyNoInteractions(productLookupService, productAvailabilityPort);
    }
}
