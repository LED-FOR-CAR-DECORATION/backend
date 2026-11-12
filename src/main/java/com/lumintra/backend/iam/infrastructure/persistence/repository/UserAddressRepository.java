package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddressEntity, Long> {
    Optional<UserAddressEntity> findByAddressIdAndCustomerId(Long addressId, Long customerId);

    boolean existsByCustomerId(Long customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UserAddressEntity a
            SET a.isDefault = false
            WHERE a.customerId = :customerId
              AND a.isDefault = true
            """)
    int clearDefaultByCustomerId(@Param("customerId") Long customerId);

    @Query("""
            SELECT a
            FROM UserAddressEntity a
            WHERE a.customerId = :customerId
            ORDER BY a.isDefault DESC, a.updatedAt DESC NULLS LAST, a.createdAt DESC, a.addressId DESC
            """)
    List<UserAddressEntity> findAllByCustomerIdOrdered(@Param("customerId") Long customerId);
}
