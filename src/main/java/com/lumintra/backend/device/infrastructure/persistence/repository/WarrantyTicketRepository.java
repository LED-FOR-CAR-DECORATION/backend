package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WarrantyTicketRepository extends JpaRepository<WarrantyTicketEntity, Long>, JpaSpecificationExecutor<WarrantyTicketEntity> {

    List<WarrantyTicketEntity> findByDeviceSerialNumberOrderByCreatedAtDesc(String deviceSerialNumber);

    boolean existsByDeviceSerialNumberAndStatusIn(String deviceSerialNumber, Collection<WarrantyTicketStatus> statuses);

    Optional<WarrantyTicketEntity> findFirstByDeviceSerialNumberAndStatusInOrderByCreatedAtDesc(
            String deviceSerialNumber,
            Collection<WarrantyTicketStatus> statuses
    );

    List<WarrantyTicketEntity> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
