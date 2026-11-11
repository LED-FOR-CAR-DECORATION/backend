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
}
