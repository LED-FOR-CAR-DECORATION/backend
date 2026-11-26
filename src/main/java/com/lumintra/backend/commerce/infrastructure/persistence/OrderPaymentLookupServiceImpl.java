package com.lumintra.backend.commerce.infrastructure.persistence;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderPaymentLookupServiceImpl implements OrderPaymentLookupService {

    private final OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderPaymentSnapshot> findByOrderId(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId)
                .map(order -> new OrderPaymentSnapshot(
                        order.getOrderId(),
                        order.getCustomerId(),
                        order.getTotalAmount(),
                        order.getStatus(),
                        order.getExpiresAt()
                ));
    }
}
