package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommissionMessageRepository extends JpaRepository<CommissionMessageEntity, Long> {

    List<CommissionMessageEntity> findAllByCommissionIdOrderByCreatedAtAsc(Long commissionId);
}
