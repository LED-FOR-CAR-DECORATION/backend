package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FirmwareVersionRepository extends JpaRepository<FirmwareVersionEntity, Long>,
        JpaSpecificationExecutor<FirmwareVersionEntity> {

    /** Batch lookup for a page of models; one query regardless of page size. */
    List<FirmwareVersionEntity> findByModelIdInAndStatus(Collection<Long> modelIds, FirmwareStatus status);

    /** Every release of a model, any status; used to keep versions unique and increasing. */
    List<FirmwareVersionEntity> findByModelId(Long modelId);
}
