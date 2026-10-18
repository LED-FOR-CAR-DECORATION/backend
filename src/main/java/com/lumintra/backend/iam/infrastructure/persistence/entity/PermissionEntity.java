package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "permissions")
@Getter
@Setter
public class PermissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long permissionId;

    @Column(name = "permission_name", unique = true, nullable = false)
    private String permissionName;

    private String description;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();
}