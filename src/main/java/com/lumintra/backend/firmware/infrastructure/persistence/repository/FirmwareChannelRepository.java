package com.lumintra.backend.firmware.infrastructure.persistence.repository;
import com.lumintra.backend.firmware.infrastructure.persistence.entity.FirmwareChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FirmwareChannelRepository extends JpaRepository<FirmwareChannelEntity,String> {}
