package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscriptionEntity, Long> {

    boolean existsByCustomerIdAndStatus(Long customerId, String status);

    List<CustomerSubscriptionEntity> findByCustomerIdAndStatusOrderByActivatedAtDesc(Long customerId, String status);
}
