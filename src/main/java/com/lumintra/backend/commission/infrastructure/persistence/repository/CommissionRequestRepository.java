package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRequestRepository extends JpaRepository<CommissionRequestEntity, Long> {

    /**
     * Loads a commission with a pessimistic write lock to serialize state transitions
     * (e.g. CMS-18 customer approval) and avoid duplicate side effects.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CommissionRequestEntity c WHERE c.commissionId = :id")
    Optional<CommissionRequestEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommissionRequestEntity c where c.commissionId = :id")
    Optional<CommissionRequestEntity> findForRevisionConfirmation(@Param("id") Long id);

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

    /** CMS-28: ids of commissions in {@code status} whose SLA deadline is at or before {@code now}, oldest first. */
    @Query("""
            select c.commissionId from CommissionRequestEntity c
            where c.status = :status and c.slaDueAt is not null and c.slaDueAt <= :now
            order by c.slaDueAt asc, c.commissionId asc""")
    List<Long> findIdsWithSlaDueAtOrBefore(@Param("status") CommissionStatus status, @Param("now") Instant now);

    /** CMS-27: ids of PREVIEW_DELIVERED commissions whose auto-complete deadline is at or before {@code now}, oldest first. */
    @Query("""
            select c.commissionId from CommissionRequestEntity c
            where c.status = com.lumintra.backend.commission.domain.model.CommissionStatus.PREVIEW_DELIVERED
              and c.autoCompleteAt is not null and c.autoCompleteAt <= :now
            order by c.autoCompleteAt asc, c.commissionId asc""")
    List<Long> findIdsDueForAutoCompletion(@Param("now") Instant now);

    /** CMS-28: flags every not-yet-flagged commission in {@code statuses} whose SLA deadline has passed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CommissionRequestEntity c
            set c.isSlaOverdue = true, c.updatedAt = :now
            where c.isSlaOverdue = false and c.slaDueAt is not null and c.slaDueAt < :now
              and c.status in :statuses""")
    int flagSlaOverdue(@Param("statuses") Collection<CommissionStatus> statuses, @Param("now") Instant now);
}
