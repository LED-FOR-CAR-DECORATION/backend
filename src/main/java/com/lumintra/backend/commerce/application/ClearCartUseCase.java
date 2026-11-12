package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.CartMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClearCartUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClearCartUseCase.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse execute(Long customerId) {
        Optional<CartEntity> existingCart = cartRepository.findByCustomerId(customerId);
        if (existingCart.isEmpty()) {
            log.info("Shopping cart clear skipped because no cart exists [customerId={}]", customerId);
            return cartMapper.toEmptyCartResponse(Instant.now());
        }

        CartEntity cart = existingCart.get();
        int deletedCount = cartItemRepository.deleteAllByCartId(cart.getCartId());
        cart.touch();
        CartEntity savedCart = cartRepository.save(cart);

        log.info("Shopping cart cleared [customerId={}] [cartId={}] [deletedItemCount={}]",
                customerId, savedCart.getCartId(), deletedCount);

        return cartMapper.toCartResponse(
                savedCart,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }
}
