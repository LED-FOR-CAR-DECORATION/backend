package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRevisionRequestRepository extends JpaRepository<CommissionRevisionRequestEntity, Long> {

    Optional<CommissionRevisionRequestEntity> findByMessageId(Long messageId);

    List<CommissionRevisionRequestEntity> findAllByCommissionIdAndStatus(
            Long commissionId, CommissionRevisionRequestStatus status);

    Optional<CommissionRevisionRequestEntity> findFirstByCommissionIdAndStatusInOrderByRevisionRequestIdDesc(
            Long commissionId, Collection<CommissionRevisionRequestStatus> statuses);

    List<CommissionRevisionRequestEntity> findAllByCommissionIdAndStatusNotOrderByRevisionRequestIdAsc(
            Long commissionId, CommissionRevisionRequestStatus status);
}
