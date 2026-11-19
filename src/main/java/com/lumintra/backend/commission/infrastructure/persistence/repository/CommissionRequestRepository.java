package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface CommissionRequestRepository extends JpaRepository<CommissionRequestEntity, Long> {

    Page<CommissionRequestEntity> findAllByCustomerId(Long customerId, Pageable pageable);

    Page<CommissionRequestEntity> findAllByCustomerIdAndStatus(
            Long customerId,
            CommissionStatus status,
            Pageable pageable
    );

    Page<CommissionRequestEntity> findAllByStatus(CommissionStatus status, Pageable pageable);

    Page<CommissionRequestEntity> findAllByAssignedDesignerId(Long assignedDesignerId, Pageable pageable);

    Page<CommissionRequestEntity> findAllByAssignedDesignerIdAndStatus(
            Long assignedDesignerId,
            CommissionStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select commission from CommissionRequestEntity commission where commission.commissionId = :id")
    Optional<CommissionRequestEntity> findForRevisionCard(@Param("id") Long id);
}
