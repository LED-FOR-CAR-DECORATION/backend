package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface OwnedCharacterRepository extends JpaRepository<OwnedCharacterEntity, Long> {

    boolean existsByCustomerIdAndVersionIdAndIsRevokedFalse(Long customerId, Long versionId);

    boolean existsByCustomerIdAndVersionIdInAndIsRevokedFalse(Long customerId, Collection<Long> versionIds);

    List<OwnedCharacterEntity> findByCustomerIdAndIsRevokedFalseOrderByGrantedAtDesc(Long customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OwnedCharacterEntity oc
            SET oc.isRevoked = true,
                oc.updatedAt = :updatedAt
            WHERE oc.customerId = :customerId
              AND oc.orderItemId IN :orderItemIds
              AND oc.isRevoked = false
            """)
    int revokeByCustomerIdAndOrderItemIds(
            @Param("customerId") Long customerId,
            @Param("orderItemIds") Collection<Long> orderItemIds,
            @Param("updatedAt") Instant updatedAt);
}
