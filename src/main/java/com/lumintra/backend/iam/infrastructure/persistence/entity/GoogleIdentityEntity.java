package com.lumintra.backend.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "google_identities")
@Getter @Setter
public class GoogleIdentityEntity {
    @Id @Column(length = 255) private String subject;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true) private UserAccountEntity account;
}
