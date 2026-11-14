package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface LedInstanceRepository extends JpaRepository<LedInstanceEntity, Long>, JpaSpecificationExecutor<LedInstanceEntity> {

    boolean existsBySerialNumber(String serialNumber);

    boolean existsByMacAddress(String macAddress);

    Optional<LedInstanceEntity> findBySerialNumber(String serialNumber);

    Optional<LedInstanceEntity> findBySerialNumberAndMacAddress(String serialNumber, String macAddress);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LedInstanceEntity i
            SET i.ownerCustomerId = :customerId,
                i.deviceName = :deviceName,
                i.status = com.lumintra.backend.device.domain.model.LedInstanceStatus.BOUND,
                i.boundAt = :boundAt,
                i.warrantyExpiresAt = :warrantyExpiresAt,
                i.updatedAt = :boundAt
            WHERE i.instanceId = :instanceId
              AND i.status <> com.lumintra.backend.device.domain.model.LedInstanceStatus.BOUND
            """)
    int bindIfUnbound(
            @Param("instanceId") Long instanceId,
            @Param("customerId") Long customerId,
            @Param("deviceName") String deviceName,
            @Param("boundAt") Instant boundAt,
            @Param("warrantyExpiresAt") Instant warrantyExpiresAt);
}
