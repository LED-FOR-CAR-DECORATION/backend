package com.lumintra.backend.commerce.infrastructure.persistence.repository;

import com.lumintra.backend.commerce.infrastructure.persistence.entity.OwnedCharacterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface OwnedCharacterRepository extends JpaRepository<OwnedCharacterEntity, Long> {

    boolean existsByCustomerIdAndVersionIdAndIsRevokedFalse(Long customerId, Long versionId);

    boolean existsByCustomerIdAndVersionIdInAndIsRevokedFalse(Long customerId, Collection<Long> versionIds);
}
