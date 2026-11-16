package com.lumintra.backend.commission.infrastructure.persistence.repository;

import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionPriceConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionPriceConfigRepository extends JpaRepository<CommissionPriceConfigEntity, Long> {

    Optional<CommissionPriceConfigEntity> findByRuleKey(String ruleKey);

    boolean existsByRuleKey(String ruleKey);

    boolean existsByRuleKeyIgnoreCase(String ruleKey);

    List<CommissionPriceConfigEntity> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<CommissionPriceConfigEntity> findAllByOrderByDisplayOrderAsc();

    @Query("SELECT DISTINCT c FROM CommissionPriceConfigEntity c LEFT JOIN FETCH c.options o WHERE c.isActive = true ORDER BY c.displayOrder ASC")
    List<CommissionPriceConfigEntity> findActiveWithActiveOptions();

    List<CommissionPriceConfigEntity> findByIsSystemAndIsActiveOrderByDisplayOrderAsc(boolean isSystem, boolean isActive);
}
