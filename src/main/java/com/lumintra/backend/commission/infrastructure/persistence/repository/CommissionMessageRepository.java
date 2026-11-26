package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionMessageRepository extends JpaRepository<CommissionMessageEntity, Long> {

    Optional<CommissionMessageEntity>
            findFirstByCommissionIdAndMessageTypeOrderByCreatedAtDescMessageIdDesc(
                    Long commissionId, CommissionMessageType messageType);
    Optional<CommissionMessageEntity> findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
            Long commissionId, CommissionMessageType messageType);

    List<CommissionMessageEntity> findAllByCommissionIdOrderByCreatedAtAsc(Long commissionId);
}
