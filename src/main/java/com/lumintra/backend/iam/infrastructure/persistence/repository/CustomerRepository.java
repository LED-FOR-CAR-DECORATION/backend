package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.infrastructure.persistence.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    @Query("""
            SELECT new com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot(
                a.accountId,
                c.fullName,
                c.phone,
                a.email
            )
            FROM UserAccountEntity a
            LEFT JOIN CustomerEntity c ON c.accountId = a.accountId
            WHERE a.accountId IN :customerIds
            """)
    List<CustomerProfileSnapshot> findProfileSnapshotsByCustomerIds(@Param("customerIds") Collection<Long> customerIds);
}
