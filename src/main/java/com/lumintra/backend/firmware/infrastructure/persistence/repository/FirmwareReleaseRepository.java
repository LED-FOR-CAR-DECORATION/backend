package com.lumintra.backend.firmware.infrastructure.persistence.repository;
import com.lumintra.backend.firmware.infrastructure.persistence.entity.FirmwareReleaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FirmwareReleaseRepository extends JpaRepository<FirmwareReleaseEntity,String> {
    List<FirmwareReleaseEntity> findAllByOrderByCreatedAtDesc();
}
