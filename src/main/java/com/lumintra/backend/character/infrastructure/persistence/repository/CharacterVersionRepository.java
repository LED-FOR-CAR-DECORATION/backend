package com.lumintra.backend.character.infrastructure.persistence.repository;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CharacterVersionRepository extends JpaRepository<CharacterVersionEntity, Long> {

    Optional<CharacterVersionEntity> findFirstByProductIdAndIsCurrentTrue(Long productId);

    Optional<CharacterVersionEntity> findFirstByProductIdOrderByVersionNumberDesc(Long productId);
}
