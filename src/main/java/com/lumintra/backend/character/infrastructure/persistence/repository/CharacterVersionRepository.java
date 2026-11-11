package com.lumintra.backend.character.infrastructure.persistence.repository;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CharacterVersionRepository extends JpaRepository<CharacterVersionEntity, Long> {

    Optional<CharacterVersionEntity> findFirstByProductIdAndIsCurrentTrue(Long productId);

    Optional<CharacterVersionEntity> findFirstByProductIdOrderByVersionNumberDesc(Long productId);

    List<CharacterVersionEntity> findByProductIdInOrderByIsCurrentDescVersionNumberDesc(Collection<Long> productIds);

    @EntityGraph(attributePaths = "character")
    Optional<CharacterVersionEntity> findWithCharacterFirstByProductIdAndIsCurrentTrue(Long productId);

    @EntityGraph(attributePaths = "character")
    Optional<CharacterVersionEntity> findWithCharacterFirstByProductIdOrderByVersionNumberDesc(Long productId);

    @EntityGraph(attributePaths = "character")
    List<CharacterVersionEntity> findWithCharacterByVersionIdIn(Collection<Long> versionIds);
}
