package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Long> {
    @Query("select count(tag) > 0 from TagEntity tag where tag.tagId <> :id and lower(trim(tag.name)) = lower(:name)")
    boolean existsByNormalizedNameExcluding(@Param("name") String name, @Param("id") long id);

    Optional<TagEntity> findByName(String name);

    // Serialize tag writes while checking uniqueness without changing the database schema.
    @Modifying
    @Query(value = "LOCK TABLE tag IN SHARE ROW EXCLUSIVE MODE", nativeQuery = true)
    void lockForCreation();

    @Query("select count(tag) > 0 from TagEntity tag where lower(trim(tag.name)) = lower(:name)")
    boolean existsByNormalizedName(@Param("name") String name);
}
