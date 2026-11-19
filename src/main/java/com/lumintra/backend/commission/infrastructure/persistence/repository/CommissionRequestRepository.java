package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommissionRequestRepository extends JpaRepository<CommissionRequestEntity, Long> {

    /**
     * Loads a commission with a pessimistic write lock to serialize state transitions (e.g. CMS-18
     * customer approval) and avoid duplicate side effects.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CommissionRequestEntity c WHERE c.commissionId = :id")
    Optional<CommissionRequestEntity> findByIdForUpdate(@Param("id") Long id);

    Page<CommissionRequestEntity> findAllByCustomerId(Long customerId, Pageable pageable);

    Page<CommissionRequestEntity> findAllByCustomerIdAndStatus(
            Long customerId, CommissionStatus status, Pageable pageable);

    Page<CommissionRequestEntity> findAllByStatus(CommissionStatus status, Pageable pageable);

    Page<CommissionRequestEntity> findAllByAssignedDesignerId(
            Long assignedDesignerId, Pageable pageable);

    Page<CommissionRequestEntity> findAllByAssignedDesignerIdAndStatus(
            Long assignedDesignerId, CommissionStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select commission from CommissionRequestEntity commission where"
                + " commission.commissionId = :id")
    Optional<CommissionRequestEntity> findForRevisionCard(@Param("id") Long id);
}
