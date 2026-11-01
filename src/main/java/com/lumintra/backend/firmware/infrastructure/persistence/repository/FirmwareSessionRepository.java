package com.lumintra.backend.firmware.infrastructure.persistence.repository;
import com.lumintra.backend.firmware.infrastructure.persistence.entity.FirmwareSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FirmwareSessionRepository extends JpaRepository<FirmwareSessionEntity,String> {}
