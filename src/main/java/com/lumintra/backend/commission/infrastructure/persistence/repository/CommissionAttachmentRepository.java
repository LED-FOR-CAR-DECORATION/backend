package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommissionAttachmentRepository extends JpaRepository<CommissionAttachmentEntity, Long> {

    List<CommissionAttachmentEntity> findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(Long commissionId);
}
