package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lumintra.backend.store.api.response.ManagedSubscriptionResponse;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionDetailRepository extends JpaRepository<SubscriptionDetailEntity, Long> {
    Optional<SubscriptionDetailEntity> findByProductId(Long productId);

    Optional<SubscriptionDetailEntity> findByPlanCode(String planCode);

    @Query("""
            select new com.lumintra.backend.store.api.response.ManagedSubscriptionResponse(
                detail.subscriptionDetailId, product.productId, detail.planCode, detail.planName,
                product.basePrice, detail.isLifetime, detail.durationDays, detail.allAccessCharacters,
                product.status, detail.createdAt)
            from SubscriptionDetailEntity detail
            join ProductEntity product on product.productId = detail.productId
            where product.productType = com.lumintra.backend.store.domain.model.ProductType.SUBSCRIPTION
            order by detail.subscriptionDetailId desc
            """)
    List<ManagedSubscriptionResponse> listManagedPlans();
}
