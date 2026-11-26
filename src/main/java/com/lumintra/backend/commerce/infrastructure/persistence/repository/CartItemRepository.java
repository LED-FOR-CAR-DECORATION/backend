package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
    List<CartItemEntity> findAllByCart_CartIdOrderByAddedAtDesc(Long cartId);

    Optional<CartItemEntity> findByCart_CartIdAndProductId(Long cartId, Long productId);

    List<CartItemEntity> findAllByCart_CartIdAndCartItemIdIn(Long cartId, Collection<Long> cartItemIds);

    @Query("""
            SELECT ci
            FROM CartItemEntity ci
            JOIN FETCH ci.cart c
            WHERE ci.cartItemId = :cartItemId
              AND c.customerId = :customerId
            """)
    Optional<CartItemEntity> findByCartItemIdAndCart_CustomerId(
            @Param("cartItemId") Long cartItemId,
            @Param("customerId") Long customerId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM CartItemEntity ci
            WHERE ci.cart.cartId = :cartId
            """)
    int deleteAllByCartId(@Param("cartId") Long cartId);
}
