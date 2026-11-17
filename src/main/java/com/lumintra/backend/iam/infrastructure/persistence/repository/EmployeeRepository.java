package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository
        extends JpaRepository<EmployeeEntity, Long>, JpaSpecificationExecutor<EmployeeEntity> {
    Optional<EmployeeEntity> findByAccount_AccountId(Long accountId);

    @Override
    @EntityGraph(attributePaths = {"account", "role"})
    Page<EmployeeEntity> findAll(Specification<EmployeeEntity> specification, Pageable pageable);

    @Query("""
            select new com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot(
                e.employeeId,
                e.fullName,
                e.account.email
            )
            from EmployeeEntity e
            where e.employeeId in :employeeIds
            """)
    List<EmployeeProfileSnapshot> findProfileSnapshotsByEmployeeIds(@Param("employeeIds") Collection<Long> employeeIds);

    @Query("""
            select e.employeeId
            from EmployeeEntity e
            where e.role.roleName = 'STAFF'
              and e.account.status = com.lumintra.backend.iam.domain.model.AccountStatus.ACTIVE
            order by e.employeeId asc
            """)
    List<Long> findActiveStaffEmployeeIds();
}
