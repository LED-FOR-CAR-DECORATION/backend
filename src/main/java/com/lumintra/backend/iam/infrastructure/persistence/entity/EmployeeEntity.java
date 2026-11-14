package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "employee")
@Getter
@Setter
public class EmployeeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long employeeId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private UserAccountEntity account;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "full_name")
    private String fullName;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static EmployeeEntity createInternal(
            UserAccountEntity account, RoleEntity role, String name) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.account = account;
        employee.role = role;
        employee.fullName = name;
        return employee;
    }
}
