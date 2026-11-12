package com.lumintra.backend.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAddressEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAddressRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@DataJpaTest(
        properties = {
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
@ContextConfiguration(classes = UserAddressRepositoryQueryTest.Config.class)
class UserAddressRepositoryQueryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = UserAddressEntity.class)
    @EnableJpaRepositories(basePackageClasses = UserAddressRepository.class)
    static class Config {}

    @Autowired private EntityManager entityManager;
    @Autowired private UserAddressRepository userAddressRepository;

    private UserAddressEntity persistAddress(
            Long customerId,
            String recipientName,
            boolean isDefault,
            Instant createdAt,
            Instant updatedAt) {
        UserAddressEntity saved =
                userAddressRepository.saveAndFlush(
                        UserAddressEntity.create(
                                customerId,
                                recipientName,
                                "0901234567",
                                recipientName + " Street, District 1, HCMC",
                                new BigDecimal("10.7768890"),
                                new BigDecimal("106.7008060"),
                                isDefault));

        if (updatedAt == null) {
            entityManager
                    .createQuery(
                            "UPDATE UserAddressEntity a SET a.createdAt = :createdAt, a.updatedAt = NULL WHERE a.addressId = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", saved.getAddressId())
                    .executeUpdate();
        } else {
            entityManager
                    .createQuery(
                            "UPDATE UserAddressEntity a SET a.createdAt = :createdAt, a.updatedAt = :updatedAt WHERE a.addressId = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("updatedAt", updatedAt)
                    .setParameter("id", saved.getAddressId())
                    .executeUpdate();
        }
        return saved;
    }

    @Test
    @DisplayName(
            "IAM-22 UTCID01 [N]: AC02 & AC03 findAllByCustomerIdOrdered scopes to customerId and sorts default-first then updatedAt DESC")
    void findAllByCustomerIdOrdered_scopesCustomerAndSortsDefaultFirstThenUpdatedAtDesc() {
        UserAddressEntity olderNonDefault =
                persistAddress(
                        42L,
                        "Older NonDefault",
                        false,
                        Instant.parse("2026-11-01T01:00:00Z"),
                        Instant.parse("2026-11-02T01:00:00Z"));
        UserAddressEntity newerNonDefault =
                persistAddress(
                        42L,
                        "Newer NonDefault",
                        false,
                        Instant.parse("2026-11-03T01:00:00Z"),
                        Instant.parse("2026-11-05T01:00:00Z"));
        UserAddressEntity defaultAddress =
                persistAddress(
                        42L,
                        "Default Address",
                        true,
                        Instant.parse("2026-10-01T01:00:00Z"),
                        Instant.parse("2026-10-01T01:00:00Z"));
        persistAddress(
                99L,
                "Other Customer Default",
                true,
                Instant.parse("2026-11-10T01:00:00Z"),
                Instant.parse("2026-11-10T01:00:00Z"));
        entityManager.clear();

        List<UserAddressEntity> result = userAddressRepository.findAllByCustomerIdOrdered(42L);

        assertThat(result)
                .extracting(UserAddressEntity::getAddressId)
                .containsExactly(
                        defaultAddress.getAddressId(),
                        newerNonDefault.getAddressId(),
                        olderNonDefault.getAddressId());
        assertThat(result).allMatch(a -> a.isOwnedBy(42L));
    }

    @Test
    @DisplayName(
            "IAM-22 UTCID02 [B]: AC03 places NULL updatedAt rows last and breaks equal-timestamp ties by createdAt DESC then addressId DESC")
    void findAllByCustomerIdOrdered_placesNullUpdatedAtLastAndBreaksTiesByCreatedAtAndIdDesc() {
        entityManager
                .createNativeQuery("ALTER TABLE user_address ALTER COLUMN updated_at SET NULL")
                .executeUpdate();

        Instant sharedUpdatedAt = Instant.parse("2026-11-05T10:00:00Z");
        Instant sharedCreatedAt = Instant.parse("2026-11-05T08:00:00Z");

        UserAddressEntity nullUpdatedOlderCreated =
                persistAddress(
                        42L,
                        "Null Updated Older",
                        false,
                        Instant.parse("2026-11-01T08:00:00Z"),
                        null);
        UserAddressEntity nullUpdatedNewerCreated =
                persistAddress(42L, "Null Updated Newer", false, sharedCreatedAt, null);
        UserAddressEntity equalUpdatedOlderCreated =
                persistAddress(
                        42L,
                        "Equal Updated Older Created",
                        false,
                        Instant.parse("2026-11-02T08:00:00Z"),
                        sharedUpdatedAt);
        UserAddressEntity equalUpdatedFirstId =
                persistAddress(
                        42L,
                        "Equal Updated Tie First",
                        false,
                        sharedCreatedAt,
                        sharedUpdatedAt);
        UserAddressEntity equalUpdatedSecondId =
                persistAddress(
                        42L,
                        "Equal Updated Tie Second",
                        false,
                        sharedCreatedAt,
                        sharedUpdatedAt);
        entityManager.clear();

        List<UserAddressEntity> result = userAddressRepository.findAllByCustomerIdOrdered(42L);

        assertThat(result)
                .extracting(UserAddressEntity::getAddressId)
                .containsExactly(
                        equalUpdatedSecondId.getAddressId(),
                        equalUpdatedFirstId.getAddressId(),
                        equalUpdatedOlderCreated.getAddressId(),
                        nullUpdatedNewerCreated.getAddressId(),
                        nullUpdatedOlderCreated.getAddressId());
    }

    @Test
    @DisplayName(
            "IAM-22 UTCID03 [B]: AC02 & AC04 returns empty list when customer has no saved addresses while leaving other customer rows untouched")
    void findAllByCustomerIdOrdered_noAddressesForCustomer_returnsEmptyList() {
        persistAddress(
                99L,
                "Another Customer Address",
                true,
                Instant.parse("2026-11-10T01:00:00Z"),
                Instant.parse("2026-11-10T01:00:00Z"));
        entityManager.clear();

        List<UserAddressEntity> result = userAddressRepository.findAllByCustomerIdOrdered(42L);

        assertThat(result).isEmpty();
        assertThat(userAddressRepository.findAllByCustomerIdOrdered(99L)).hasSize(1);
    }
}
