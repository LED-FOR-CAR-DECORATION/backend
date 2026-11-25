package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.CustomerSubscriptionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscriptionEntity, Long> {

    boolean existsByCustomerIdAndStatus(Long customerId, String status);

    boolean existsByCustomerIdAndProductIdAndStatus(Long customerId, Long productId, String status);

    boolean existsByOrderItemId(Long orderItemId);

    List<CustomerSubscriptionEntity> findByCustomerIdAndStatusOrderByActivatedAtDesc(Long customerId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from CustomerSubscriptionEntity s
            where s.customerId = :customerId
              and s.status = :status
              and s.remainingCommissionRevisions > 0
            order by s.activatedAt asc, s.subscriptionId asc
            """)
    List<CustomerSubscriptionEntity> findActiveWithRemainingRevisionsForUpdate(
            @Param("customerId") Long customerId,
            @Param("status") String status
    );
}
