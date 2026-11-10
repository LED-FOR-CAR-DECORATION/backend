package com.lumintra.backend.character.infrastructure.persistence.repository;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterRepository extends JpaRepository<CharacterEntity, Long> {
}
