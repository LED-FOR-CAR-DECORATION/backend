package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, Long> {
    Optional<EmployeeEntity> findByAccount_AccountId(Long accountId);
}
