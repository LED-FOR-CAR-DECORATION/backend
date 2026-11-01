package com.lumintra.backend.firmware.infrastructure.persistence.repository;
import com.lumintra.backend.firmware.infrastructure.persistence.entity.FirmwareDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FirmwareDeviceRepository extends JpaRepository<FirmwareDeviceEntity,String> {}
