package com.lumintra.backend.iam.infrastructure.persistence.repository;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.EmployeeEntity;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Locale;

public final class ManagedUserSpecifications {
    private ManagedUserSpecifications() {}

    public static Specification<EmployeeEntity> matching(
            String role, Boolean active, String search) {
        return (root, query, cb) -> {
            var account = root.join("account");
            var employeeRole = root.join("role");
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(account.get("accountType"), AccountType.EMPLOYEE));
            predicates.add(cb.notEqual(cb.upper(employeeRole.get("roleName")), "CUSTOMER"));
            if (role != null)
                predicates.add(
                        cb.equal(
                                cb.upper(employeeRole.get("roleName")),
                                role.toUpperCase(Locale.ROOT)));
            if (active != null) {
                predicates.add(
                        active
                                ? cb.equal(account.get("status"), AccountStatus.ACTIVE)
                                : cb.notEqual(account.get("status"), AccountStatus.ACTIVE));
            }
            if (search != null) {
                String pattern =
                        "%"
                                + search.toLowerCase(Locale.ROOT)
                                        .replace("\\", "\\\\")
                                        .replace("%", "\\%")
                                        .replace("_", "\\_")
                                + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(account.get("email")), pattern, '\\'),
                                cb.like(cb.lower(root.get("fullName")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
