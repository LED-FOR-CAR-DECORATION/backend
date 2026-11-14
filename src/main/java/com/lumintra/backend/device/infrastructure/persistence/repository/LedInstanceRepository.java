package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface LedInstanceRepository extends JpaRepository<LedInstanceEntity, Long>, JpaSpecificationExecutor<LedInstanceEntity> {

    boolean existsBySerialNumber(String serialNumber);

    boolean existsByMacAddress(String macAddress);
}
