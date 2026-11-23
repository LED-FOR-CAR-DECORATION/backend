package com.lumintra.backend.device.infrastructure.persistence.repository;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FirmwareVersionRepository extends JpaRepository<FirmwareVersionEntity, Long>,
        JpaSpecificationExecutor<FirmwareVersionEntity> {

    /** Batch lookup for a page of models; one query regardless of page size. */
    List<FirmwareVersionEntity> findByModelIdInAndStatus(Collection<Long> modelIds, FirmwareStatus status);

    /** Every release of a model, any status; used to keep versions unique and increasing. */
    List<FirmwareVersionEntity> findByModelId(Long modelId);

    /** Reads only the model id, so the model row can be locked before the release itself is loaded. */
    @Query("SELECT f.modelId FROM FirmwareVersionEntity f WHERE f.firmwareId = :firmwareId")
    Optional<Long> findModelIdByFirmwareId(@Param("firmwareId") Long firmwareId);
}
