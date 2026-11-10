package com.lumintra.backend.commerce.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "cart_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_item_cart_product",
                columnNames = {"cart_id", "product_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long cartItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public static CartItemEntity create(CartEntity cart, Long productId, int quantity, BigDecimal unitPrice) {
        CartItemEntity entity = new CartItemEntity();
        entity.setCart(cart);
        entity.setProductId(productId);
        entity.setQuantity(quantity);
        entity.setUnitPrice(unitPrice);
        entity.setAddedAt(Instant.now());
        return entity;
    }

    public static CartItemEntity of(
            Long cartItemId,
            CartEntity cart,
            Long productId,
            int quantity,
            BigDecimal unitPrice,
            Instant addedAt) {
        CartItemEntity entity = new CartItemEntity();
        entity.setCartItemId(cartItemId);
        entity.setCart(cart);
        entity.setProductId(productId);
        entity.setQuantity(quantity);
        entity.setUnitPrice(unitPrice);
        entity.setAddedAt(addedAt);
        return entity;
    }

    public void updateQuantityAndPrice(int newQuantity, BigDecimal latestUnitPrice) {
        this.quantity = newQuantity;
        if (latestUnitPrice != null) {
            this.unitPrice = latestUnitPrice;
        }
    }
}
