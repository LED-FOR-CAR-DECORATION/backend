package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommissionSubmissionRepository extends JpaRepository<CommissionSubmissionEntity, Long> {

    List<CommissionSubmissionEntity> findAllByCommissionIdOrderByVersionNumberAscSubmissionIdAsc(Long commissionId);

    List<CommissionSubmissionEntity> findAllByCommissionIdAndStatusInOrderByVersionNumberAscSubmissionIdAsc(
            Long commissionId,
            Collection<CommissionSubmissionStatus> statuses);
}
