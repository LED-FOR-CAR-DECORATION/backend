package com.lumintra.backend.character.infrastructure.persistence.repository;

import com.lumintra.backend.character.domain.model.CharacterType;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class CharacterSpecifications {

    private CharacterSpecifications() {
    }

    public static Specification<CharacterEntity> forAdmin(
            String query,
            Long creatorEmployeeId,
            CharacterType characterType,
            Long commissionId) {
        return (root, criteriaQuery, cb) -> {
            var predicates = cb.conjunction();

            if (creatorEmployeeId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("creatorEmployeeId"), creatorEmployeeId));
            }

            if (characterType != null) {
                predicates = cb.and(predicates, cb.equal(root.get("characterType"), characterType));
            }

            if (commissionId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("commissionId"), commissionId));
            }

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                var nameMatch = cb.like(cb.lower(root.get("characterName")), pattern);
                var descMatch = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern);
                predicates = cb.and(predicates, cb.or(nameMatch, descMatch));
            }

            return predicates;
        };
    }

    public static Specification<CharacterEntity> forDesigner(
            Long designerEmployeeId,
            String query,
            CharacterType characterType,
            Long commissionId) {
        return (root, criteriaQuery, cb) -> {
            var predicates = cb.conjunction();
            predicates = cb.and(predicates, cb.equal(root.get("creatorEmployeeId"), designerEmployeeId));

            if (characterType != null) {
                predicates = cb.and(predicates, cb.equal(root.get("characterType"), characterType));
            }

            if (commissionId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("commissionId"), commissionId));
            }

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                var nameMatch = cb.like(cb.lower(root.get("characterName")), pattern);
                var descMatch = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern);
                predicates = cb.and(predicates, cb.or(nameMatch, descMatch));
            }

            return predicates;
        };
    }
}
