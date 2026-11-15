package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionPriceOptionRepository extends JpaRepository<CommissionPriceOptionEntity, Long> {

    List<CommissionPriceOptionEntity> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<CommissionPriceOptionEntity> findAllByOrderByDisplayOrderAsc();

    Optional<CommissionPriceOptionEntity> findByOptionCode(String optionCode);

    Optional<CommissionPriceOptionEntity> findByConfigConfigIdAndOptionCode(Long configId, String optionCode);

    List<CommissionPriceOptionEntity> findByConfigConfigId(Long configId);
}
