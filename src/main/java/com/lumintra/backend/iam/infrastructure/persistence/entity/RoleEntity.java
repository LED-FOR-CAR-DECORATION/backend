package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class RoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;

    @Column(name = "role_name", unique = true, nullable = false)
    private String roleName;

    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}
