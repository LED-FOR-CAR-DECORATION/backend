package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.ClearCartUseCase;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.CartMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClearCartUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    private ClearCartUseCase clearCartUseCase;

    @BeforeEach
    void setUp() {
        CartMapper cartMapper = new CartMapper();
        clearCartUseCase = new ClearCartUseCase(cartRepository, cartItemRepository, cartMapper);
    }

    @Test
    @DisplayName("UTCID01 [N]: AC02, AC04 & AC05 bulk-deletes all items, retains parent cart, touches updatedAt, and returns empty CartResponse")
    void execute_existingCartWithItems_bulkDeletesRetainsCartTouchesTimestampAndReturnsEmptyCart() {
        Long customerId = 42L;
        Instant oldUpdatedAt = Instant.parse("2026-11-10T02:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, oldUpdatedAt);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.deleteAllByCartId(101L)).thenReturn(4);
        when(cartRepository.save(cart)).thenReturn(cart);

        CartResponse response = clearCartUseCase.execute(customerId);

        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.items()).isEmpty();
        assertThat(cart.getUpdatedAt()).isAfter(oldUpdatedAt);
        assertThat(response.updatedAt()).isEqualTo(cart.getUpdatedAt());

        InOrder inOrder = inOrder(cartRepository, cartItemRepository);
        inOrder.verify(cartRepository).findByCustomerId(customerId);
        inOrder.verify(cartItemRepository).deleteAllByCartId(101L);
        inOrder.verify(cartRepository).save(cart);

        verify(cartItemRepository, never()).delete(any());
        verify(cartItemRepository, never()).deleteAllByIdInBatch(any());
        verify(cartRepository, never()).delete(any());
    }

    @Test
    @DisplayName("UTCID02 [B]: AC02 & AC04 clearing an already-empty existing cart succeeds idempotently and updates updatedAt")
    void execute_existingAlreadyEmptyCart_succeedsIdempotentlyAndRefreshesTimestamp() {
        Long customerId = 42L;
        Instant oldUpdatedAt = Instant.parse("2026-11-10T02:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, oldUpdatedAt);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.deleteAllByCartId(101L)).thenReturn(0);
        when(cartRepository.save(cart)).thenReturn(cart);

        CartResponse response = clearCartUseCase.execute(customerId);

        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.items()).isEmpty();
        assertThat(response.updatedAt()).isAfter(oldUpdatedAt);

        verify(cartItemRepository).deleteAllByCartId(101L);
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("UTCID03 [B]: AC02 & AC05 customer with no cart row succeeds idempotently without creating a cart, returning null cartId and empty items")
    void execute_noCartExistsForCustomer_returnsEmptyResponseWithNullCartIdWithoutCreatingCart() {
        Long customerId = 42L;
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());

        Instant before = Instant.now();
        CartResponse response = clearCartUseCase.execute(customerId);
        Instant after = Instant.now();

        assertThat(response.cartId()).isNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.updatedAt()).isBetween(before, after);

        verify(cartRepository).findByCustomerId(customerId);
        verify(cartRepository, never()).save(any());
        verifyNoInteractions(cartItemRepository);
    }

    @Test
    @DisplayName("UTCID04 [N]: AC03 clears cart unconditionally across digital, archived, unpublished, or out-of-stock items via single bulk delete")
    void execute_cartWithMixedOrUnavailableItems_clearsUnconditionallyInSingleBulkQuery() {
        Long customerId = 42L;
        Instant oldUpdatedAt = Instant.parse("2026-11-10T02:00:00Z");
        CartEntity cart = CartEntity.of(105L, customerId, oldUpdatedAt);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.deleteAllByCartId(105L)).thenReturn(3);
        when(cartRepository.save(cart)).thenReturn(cart);

        CartResponse response = clearCartUseCase.execute(customerId);

        assertThat(response.cartId()).isEqualTo(105L);
        assertThat(response.items()).isEmpty();
        verify(cartItemRepository, times(1)).deleteAllByCartId(105L);
        verify(cartItemRepository, never()).findAllByCart_CartIdOrderByAddedAtDesc(any());
    }

    @Test
    @DisplayName("UTCID05 [A]: AC04 propagates persistence exceptions during bulk delete without saving parent cart")
    void execute_bulkDeletePersistenceFailure_propagatesExceptionWithoutSavingCart() {
        Long customerId = 42L;
        Instant oldUpdatedAt = Instant.parse("2026-11-10T02:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, oldUpdatedAt);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.deleteAllByCartId(101L)).thenThrow(new RuntimeException("DB connection error"));

        assertThatThrownBy(() -> clearCartUseCase.execute(customerId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB connection error");

        verify(cartRepository, never()).save(any());
    }
}
