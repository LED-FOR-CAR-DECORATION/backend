package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        properties = {
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
@ContextConfiguration(classes = CartItemRepositoryQueryTest.Config.class)
class CartItemRepositoryQueryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = CartEntity.class)
    @EnableJpaRepositories(basePackageClasses = {CartRepository.class, CartItemRepository.class})
    static class Config {}

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    @DisplayName("UTCID01 [N]: AC02 findByCartItemIdAndCart_CustomerId returns item with initialized CartEntity when owned by customer")
    void findByCartItemIdAndCartCustomerId_ownedItem_returnsItemWithCart() {
        CartEntity cart = cartRepository.save(CartEntity.create(42L));
        CartItemEntity item = cartItemRepository.save(
                CartItemEntity.create(cart, 12L, 2, new BigDecimal("350000.00"))
        );
        entityManager.flush();
        entityManager.clear();

        Optional<CartItemEntity> found =
                cartItemRepository.findByCartItemIdAndCart_CustomerId(item.getCartItemId(), 42L);

        assertThat(found).isPresent();
        assertThat(found.get().getProductId()).isEqualTo(12L);
        assertThat(found.get().getQuantity()).isEqualTo(2);
        assertThat(found.get().getCart().getCartId()).isEqualTo(cart.getCartId());
        assertThat(found.get().getCart().getCustomerId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("UTCID02 [A]: AC02 anti-IDOR query returns empty for another customer's itemId or missing itemId without creating cart")
    void findByCartItemIdAndCartCustomerId_unownedOrMissing_returnsEmptyWithoutCreatingCart() {
        CartEntity ownerCart = cartRepository.save(CartEntity.create(99L));
        CartItemEntity ownersItem = cartItemRepository.save(
                CartItemEntity.create(ownerCart, 12L, 1, new BigDecimal("350000.00"))
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(cartItemRepository.findByCartItemIdAndCart_CustomerId(ownersItem.getCartItemId(), 42L)).isEmpty();
        assertThat(cartItemRepository.findByCartItemIdAndCart_CustomerId(99999L, 42L)).isEmpty();
        assertThat(cartRepository.findByCustomerId(42L)).isEmpty();
        assertThat(cartRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTCID03 [N]: AC06 persisting quantity and unitPrice updates preserves addedAt and refreshes cart.updatedAt")
    void saveUpdatedQuantityAndPrice_persistsInDatabase() {
        CartEntity cart = cartRepository.save(CartEntity.create(42L));
        CartItemEntity item = cartItemRepository.save(
                CartItemEntity.create(cart, 12L, 2, new BigDecimal("320000.00"))
        );
        entityManager.flush();
        entityManager.clear();

        CartItemEntity managed = cartItemRepository
                .findByCartItemIdAndCart_CustomerId(item.getCartItemId(), 42L)
                .orElseThrow();
        var originalAddedAt = managed.getAddedAt();

        managed.updateQuantityAndPrice(5, new BigDecimal("350000.00"));
        cartItemRepository.save(managed);
        managed.getCart().touch();
        cartRepository.save(managed.getCart());
        entityManager.flush();
        entityManager.clear();

        CartItemEntity reloaded = cartItemRepository
                .findByCartItemIdAndCart_CustomerId(item.getCartItemId(), 42L)
                .orElseThrow();
        assertThat(reloaded.getQuantity()).isEqualTo(5);
        assertThat(reloaded.getUnitPrice()).isEqualByComparingTo("350000.00");
        assertThat(reloaded.getAddedAt()).isEqualTo(originalAddedAt);
    }

    @Test
    @DisplayName("COM-04 UTCID04 [N]: AC05 deleting last cart item removes only cart_item row, keeps parent cart, and refreshes updatedAt")
    void deleteLastCartItem_removesOnlyItemRowAndRetainsCartWithUpdatedTimestamp() {
        CartEntity cart = cartRepository.save(CartEntity.create(42L));
        CartItemEntity item = cartItemRepository.save(
                CartItemEntity.create(cart, 12L, 1, new BigDecimal("350000.00"))
        );
        entityManager.flush();
        entityManager.clear();

        CartItemEntity managed = cartItemRepository
                .findByCartItemIdAndCart_CustomerId(item.getCartItemId(), 42L)
                .orElseThrow();
        var originalUpdatedAt = managed.getCart().getUpdatedAt();

        cartItemRepository.delete(managed);
        cartItemRepository.flush();
        managed.getCart().touch();
        cartRepository.save(managed.getCart());
        entityManager.flush();
        entityManager.clear();

        assertThat(cartItemRepository.findById(item.getCartItemId())).isEmpty();
        assertThat(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(cart.getCartId())).isEmpty();
        CartEntity retainedCart = cartRepository.findByCustomerId(42L).orElseThrow();
        assertThat(retainedCart.getCartId()).isEqualTo(cart.getCartId());
        assertThat(retainedCart.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("COM-04 UTCID05 [N]: AC05 & AC06 deleting one cart item preserves remaining items ordered by addedAt DESC")
    void deleteOneCartItem_leavesOtherItemsInCartOrderedDesc() {
        CartEntity cart = cartRepository.save(CartEntity.create(42L));
        CartItemEntity firstItem = cartItemRepository.save(
                CartItemEntity.create(cart, 12L, 2, new BigDecimal("350000.00"))
        );
        CartItemEntity secondItem = cartItemRepository.save(
                CartItemEntity.create(cart, 30L, 1, new BigDecimal("59000.00"))
        );
        entityManager.flush();
        entityManager.clear();

        CartItemEntity toDelete = cartItemRepository
                .findByCartItemIdAndCart_CustomerId(firstItem.getCartItemId(), 42L)
                .orElseThrow();

        cartItemRepository.delete(toDelete);
        cartItemRepository.flush();

        var remaining = cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(cart.getCartId());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getCartItemId()).isEqualTo(secondItem.getCartItemId());
        assertThat(remaining.get(0).getProductId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("COM-05 UTCID06 [N]: AC04 bulk deleteAllByCartId removes all target cart items only, retains parent cart with refreshed updatedAt, and leaves other customer carts untouched")
    void deleteAllByCartId_clearsAllTargetCartItemsOnlyAndPreservesOtherCustomerCart() {
        CartEntity customerCart = cartRepository.save(CartEntity.create(42L));
        CartEntity otherCart = cartRepository.save(CartEntity.create(99L));
        cartItemRepository.save(CartItemEntity.create(customerCart, 12L, 2, new BigDecimal("350000.00")));
        cartItemRepository.save(CartItemEntity.create(customerCart, 30L, 1, new BigDecimal("59000.00")));
        cartItemRepository.save(CartItemEntity.create(customerCart, 31L, 1, new BigDecimal("99000.00")));
        CartItemEntity otherItem = cartItemRepository.save(
                CartItemEntity.create(otherCart, 12L, 4, new BigDecimal("350000.00"))
        );
        entityManager.flush();

        Instant oldUpdatedAt = Instant.parse("2026-11-10T01:00:00Z");
        entityManager.createQuery("UPDATE CartEntity c SET c.updatedAt = :old WHERE c.cartId = :id")
                .setParameter("old", oldUpdatedAt)
                .setParameter("id", customerCart.getCartId())
                .executeUpdate();
        entityManager.clear();

        CartEntity managedCart = cartRepository.findByCustomerId(42L).orElseThrow();
        int deletedCount = cartItemRepository.deleteAllByCartId(managedCart.getCartId());
        managedCart.touch();
        cartRepository.save(managedCart);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedCount).isEqualTo(3);
        assertThat(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(customerCart.getCartId())).isEmpty();
        CartEntity retainedCart = cartRepository.findByCustomerId(42L).orElseThrow();
        assertThat(retainedCart.getCartId()).isEqualTo(customerCart.getCartId());
        assertThat(retainedCart.getUpdatedAt()).isAfter(oldUpdatedAt);

        var otherItems = cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(otherCart.getCartId());
        assertThat(otherItems).hasSize(1);
        assertThat(otherItems.get(0).getCartItemId()).isEqualTo(otherItem.getCartItemId());
        assertThat(otherItems.get(0).getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("COM-05 UTCID07 [B]: AC02 & AC04 bulk deleteAllByCartId on empty cart returns 0 and keeps cart intact")
    void deleteAllByCartId_onEmptyCart_returnsZeroAndKeepsCartIntact() {
        CartEntity emptyCart = cartRepository.save(CartEntity.create(42L));
        entityManager.flush();
        entityManager.clear();

        int deletedCount = cartItemRepository.deleteAllByCartId(emptyCart.getCartId());
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedCount).isZero();
        assertThat(cartRepository.findByCustomerId(42L)).isPresent();
        assertThat(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(emptyCart.getCartId())).isEmpty();
    }
}
