package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

import org.hibernate.Session;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TagDeletionRepository {
    private final EntityManager entityManager;

    public Optional<TagEntity> findForDeletion(long id) {
        // FOR UPDATE also blocks concurrent foreign-key references to the tag.
        return Optional.ofNullable(
                entityManager
                        .unwrap(Session.class)
                        .createNativeQuery(
                                "select * from tag where tag_id = :id for update", TagEntity.class)
                        .setParameter("id", id)
                        .getSingleResultOrNull());
    }

    public void detachFromProducts(long id) {
        entityManager
                .createNativeQuery("delete from product_tag where tag_id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
