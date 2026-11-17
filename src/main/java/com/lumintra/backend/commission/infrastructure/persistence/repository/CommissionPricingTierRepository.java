package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPricingTierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionPricingTierRepository extends JpaRepository<CommissionPricingTierEntity, Long> {

    Optional<CommissionPricingTierEntity> findByTierCode(String tierCode);

    @Query("SELECT DISTINCT t FROM CommissionPricingTierEntity t LEFT JOIN FETCH t.includedOptions LEFT JOIN FETCH t.triggerOptions WHERE t.tierCode = :tierCode AND t.isActive = true")
    Optional<CommissionPricingTierEntity> findActiveWithIncludedOptionsByTierCode(@org.springframework.data.repository.query.Param("tierCode") String tierCode);

    boolean existsByTierCode(String tierCode);

    List<CommissionPricingTierEntity> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<CommissionPricingTierEntity> findAllByOrderByDisplayOrderAsc();

    @Query("SELECT DISTINCT t FROM CommissionPricingTierEntity t LEFT JOIN FETCH t.includedOptions o WHERE t.isActive = true ORDER BY t.displayOrder ASC")
    List<CommissionPricingTierEntity> findActiveWithIncludedOptions();

    Optional<CommissionPricingTierEntity> findByIsCustomTrueAndIsActiveTrue();
}
