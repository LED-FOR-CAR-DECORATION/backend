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
import org.springframework.data.domain.PageRequest;
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

    @Test
    @DisplayName(
            "IAM-23 UTCID01 [B]: AC04 existsByCustomerId distinguishes customer with no addresses from other customers")
    void existsByCustomerId_checksStrictCustomerOwnership() {
        persistAddress(
                99L,
                "Other Customer",
                true,
                Instant.parse("2026-11-10T01:00:00Z"),
                Instant.parse("2026-11-10T01:00:00Z"));
        entityManager.clear();

        assertThat(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(42L)).isFalse();
        assertThat(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(99L)).isTrue();
    }

    @Test
    @DisplayName(
            "IAM-23 UTCID02 [N]: AC04 clearDefaultByCustomerId resets only target customer's default flag")
    void clearDefaultByCustomerId_clearsOnlyTargetCustomerDefaults() {
        UserAddressEntity customer42OldDefault =
                persistAddress(
                        42L,
                        "Customer 42 Old Default",
                        true,
                        Instant.parse("2026-11-01T01:00:00Z"),
                        Instant.parse("2026-11-01T01:00:00Z"));
        UserAddressEntity customer99Default =
                persistAddress(
                        99L,
                        "Customer 99 Default",
                        true,
                        Instant.parse("2026-11-01T01:00:00Z"),
                        Instant.parse("2026-11-01T01:00:00Z"));
        entityManager.clear();

        int clearedCount = userAddressRepository.clearDefaultByCustomerId(42L);

        UserAddressEntity customer42NewDefault =
                userAddressRepository.saveAndFlush(
                        UserAddressEntity.create(
                                42L,
                                "Customer 42 New Default",
                                "0901234567",
                                "45 Le Loi, District 1, HCMC",
                                new BigDecimal("10.7731000"),
                                new BigDecimal("106.7012000"),
                                true));
        entityManager.clear();

        assertThat(clearedCount).isEqualTo(1);
        assertThat(userAddressRepository.findById(customer42OldDefault.getAddressId()))
                .get()
                .extracting(UserAddressEntity::isDefault)
                .isEqualTo(false);
        assertThat(userAddressRepository.findById(customer42NewDefault.getAddressId()))
                .get()
                .extracting(UserAddressEntity::isDefault)
                .isEqualTo(true);
        assertThat(userAddressRepository.findById(customer99Default.getAddressId()))
                .get()
                .extracting(UserAddressEntity::isDefault)
                .isEqualTo(true);
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID01 [N]: AC02 & AC04 findByAddressIdAndCustomerId updates persisted details, refreshes updatedAt, and preserves isDefault")
    void findByAddressIdAndCustomerId_updatesDetailsAndPreservesDefaultAndTimestamps() {
        Instant initialTimestamp = Instant.parse("2026-11-01T01:00:00Z");
        UserAddressEntity target =
                persistAddress(42L, "Old Name", true, initialTimestamp, initialTimestamp);
        UserAddressEntity secondary =
                persistAddress(42L, "Secondary", false, initialTimestamp, initialTimestamp);
        entityManager.clear();

        UserAddressEntity loaded =
                userAddressRepository
                        .findByAddressIdAndCustomerIdAndIsDeletedFalse(target.getAddressId(), 42L)
                        .orElseThrow();
        loaded.updateDetails(
                "Updated Name",
                "+84912345678",
                "456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City",
                new BigDecimal("10.7731235"),
                new BigDecimal("106.6987654"));
        userAddressRepository.saveAndFlush(loaded);
        entityManager.clear();

        UserAddressEntity reloaded =
                userAddressRepository.findById(target.getAddressId()).orElseThrow();
        assertThat(reloaded.getCustomerId()).isEqualTo(42L);
        assertThat(reloaded.getRecipientName()).isEqualTo("Updated Name");
        assertThat(reloaded.getRecipientPhone()).isEqualTo("+84912345678");
        assertThat(reloaded.getFullAddress())
                .isEqualTo("456 Le Loi, Ben Thanh, District 1, Ho Chi Minh City");
        assertThat(reloaded.getLatitude()).isEqualByComparingTo("10.7731235");
        assertThat(reloaded.getLongitude()).isEqualByComparingTo("106.6987654");
        assertThat(reloaded.isDefault()).isTrue();
        assertThat(reloaded.getCreatedAt()).isEqualTo(initialTimestamp);
        assertThat(reloaded.getUpdatedAt()).isAfter(initialTimestamp);

        UserAddressEntity reloadedSecondary =
                userAddressRepository.findById(secondary.getAddressId()).orElseThrow();
        assertThat(reloadedSecondary.isDefault()).isFalse();
        assertThat(reloadedSecondary.getRecipientName()).isEqualTo("Secondary");
    }

    @Test
    @DisplayName(
            "IAM-24 UTCID02 [A]: AC02 findByAddressIdAndCustomerId returns empty for missing or foreign customer addressId")
    void findByAddressIdAndCustomerId_missingOrForeignOwner_returnsEmpty() {
        UserAddressEntity foreignAddress =
                persistAddress(
                        99L,
                        "Foreign Customer",
                        true,
                        Instant.parse("2026-11-10T01:00:00Z"),
                        Instant.parse("2026-11-10T01:00:00Z"));
        entityManager.clear();

        assertThat(
                        userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(
                                foreignAddress.getAddressId(), 42L))
                .isEmpty();
        assertThat(userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(999999L, 42L)).isEmpty();
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID01 [N]: AC03 & AC04 soft-deleted rows are retained in DB but excluded from all active queries")
    void softDelete_excludesDeletedRowsAcrossAllActiveQueriesAndPreservesDatabaseRow() {
        Instant initialTimestamp = Instant.parse("2026-11-01T01:00:00Z");
        Instant deletedAt = Instant.parse("2026-11-12T04:00:00Z");

        UserAddressEntity softDeletedDefault =
                persistAddress(42L, "Deleted Default", true, initialTimestamp, initialTimestamp);
        softDeletedDefault.softDelete(deletedAt);
        userAddressRepository.saveAndFlush(softDeletedDefault);

        UserAddressEntity activeOtherCustomer =
                persistAddress(99L, "Other Customer", true, initialTimestamp, initialTimestamp);
        entityManager.clear();

        assertThat(userAddressRepository.findAllByCustomerIdOrdered(42L)).isEmpty();
        assertThat(
                        userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(
                                softDeletedDefault.getAddressId(), 42L))
                .isEmpty();
        assertThat(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(42L)).isFalse();
        assertThat(userAddressRepository.clearDefaultByCustomerId(42L)).isZero();

        UserAddressEntity persistedDeletedRow =
                userAddressRepository.findById(softDeletedDefault.getAddressId()).orElseThrow();
        assertThat(persistedDeletedRow.isDeleted()).isTrue();
        assertThat(persistedDeletedRow.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(persistedDeletedRow.isDefault()).isFalse();

        assertThat(userAddressRepository.existsByCustomerIdAndIsDeletedFalse(99L)).isTrue();
        assertThat(
                        userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(
                                activeOtherCustomer.getAddressId(), 99L))
                .isPresent();
    }

    @Test
    @DisplayName(
            "IAM-25 UTCID02 [N]: AC04 deleting default address deterministically promotes newest active address with NULLS LAST tie-breakers")
    void findActiveByCustomerIdOrdered_selectsNewestPromotionCandidateWithNullsLast() {
        entityManager
                .createNativeQuery("ALTER TABLE user_address ALTER COLUMN updated_at SET NULL")
                .executeUpdate();

        Instant deletedAt = Instant.parse("2026-11-12T05:00:00Z");
        UserAddressEntity currentDefault =
                persistAddress(
                        42L,
                        "Current Default",
                        true,
                        Instant.parse("2026-11-01T01:00:00Z"),
                        Instant.parse("2026-11-01T01:00:00Z"));
        UserAddressEntity alreadyDeletedNewer =
                persistAddress(
                        42L,
                        "Already Deleted Newer",
                        false,
                        Instant.parse("2026-11-11T01:00:00Z"),
                        Instant.parse("2026-11-11T01:00:00Z"));
        alreadyDeletedNewer.softDelete(deletedAt);
        userAddressRepository.saveAndFlush(alreadyDeletedNewer);

        UserAddressEntity nullUpdatedActive =
                persistAddress(
                        42L,
                        "Null Updated Active",
                        false,
                        Instant.parse("2026-11-09T01:00:00Z"),
                        null);
        UserAddressEntity olderActive =
                persistAddress(
                        42L,
                        "Older Active",
                        false,
                        Instant.parse("2026-11-02T01:00:00Z"),
                        Instant.parse("2026-11-03T01:00:00Z"));
        UserAddressEntity newestActive =
                persistAddress(
                        42L,
                        "Newest Active",
                        false,
                        Instant.parse("2026-11-04T01:00:00Z"),
                        Instant.parse("2026-11-08T01:00:00Z"));
        entityManager.clear();

        UserAddressEntity loadedDefault =
                userAddressRepository
                        .findByAddressIdAndCustomerIdAndIsDeletedFalse(
                                currentDefault.getAddressId(), 42L)
                        .orElseThrow();
        loadedDefault.softDelete(deletedAt);
        userAddressRepository.saveAndFlush(loadedDefault);

        List<UserAddressEntity> candidates =
                userAddressRepository.findActiveByCustomerIdOrdered(42L, PageRequest.of(0, 1));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().getAddressId()).isEqualTo(newestActive.getAddressId());

        candidates.getFirst().markDefault();
        userAddressRepository.saveAndFlush(candidates.getFirst());
        entityManager.clear();

        List<UserAddressEntity> activeAfterDelete =
                userAddressRepository.findAllByCustomerIdOrdered(42L);
        assertThat(activeAfterDelete)
                .extracting(UserAddressEntity::getAddressId)
                .containsExactly(
                        newestActive.getAddressId(),
                        olderActive.getAddressId(),
                        nullUpdatedActive.getAddressId());
        assertThat(activeAfterDelete.getFirst().isDefault()).isTrue();
    }

    @Test
    @DisplayName(
            "IAM-26 UTCID01 [N]: AC03 clearDefaultByCustomerId (clearAutomatically=true) followed by markDefault and saveAndFlush switches default cleanly without detached entity loss")
    void setDefaultFlow_clearDefaultThenMarkDefaultAndSaveAndFlush_switchesSingleDefault() {
        Instant initialTimestamp = Instant.parse("2026-11-01T01:00:00Z");
        UserAddressEntity oldDefault =
                persistAddress(42L, "Old Default", true, initialTimestamp, initialTimestamp);
        UserAddressEntity targetAddress =
                persistAddress(42L, "New Default Target", false, initialTimestamp, initialTimestamp);
        UserAddressEntity softDeletedAddress =
                persistAddress(42L, "Deleted Address", false, initialTimestamp, initialTimestamp);
        softDeletedAddress.softDelete(Instant.parse("2026-11-10T01:00:00Z"));
        userAddressRepository.saveAndFlush(softDeletedAddress);

        UserAddressEntity otherCustomerDefault =
                persistAddress(99L, "Other Customer Default", true, initialTimestamp, initialTimestamp);
        entityManager.clear();

        UserAddressEntity loadedTarget =
                userAddressRepository
                        .findByAddressIdAndCustomerIdAndIsDeletedFalse(
                                targetAddress.getAddressId(), 42L)
                        .orElseThrow();
        int clearedCount = userAddressRepository.clearDefaultByCustomerId(42L);
        loadedTarget.markDefault();
        UserAddressEntity saved = userAddressRepository.saveAndFlush(loadedTarget);
        entityManager.clear();

        assertThat(clearedCount).isEqualTo(1);
        assertThat(saved.isDefault()).isTrue();
        assertThat(saved.getUpdatedAt()).isAfter(initialTimestamp);

        UserAddressEntity reloadedOldDefault =
                userAddressRepository.findById(oldDefault.getAddressId()).orElseThrow();
        UserAddressEntity reloadedNewDefault =
                userAddressRepository.findById(targetAddress.getAddressId()).orElseThrow();
        UserAddressEntity reloadedOtherCustomer =
                userAddressRepository.findById(otherCustomerDefault.getAddressId()).orElseThrow();

        assertThat(reloadedOldDefault.isDefault()).isFalse();
        assertThat(reloadedNewDefault.isDefault()).isTrue();
        assertThat(reloadedNewDefault.getUpdatedAt()).isAfter(initialTimestamp);
        assertThat(reloadedOtherCustomer.isDefault()).isTrue();

        List<UserAddressEntity> ordered = userAddressRepository.findAllByCustomerIdOrdered(42L);
        assertThat(ordered)
                .extracting(UserAddressEntity::getAddressId)
                .containsExactly(targetAddress.getAddressId(), oldDefault.getAddressId());
    }

    @Test
    @DisplayName(
            "IAM-26 UTCID02 [B]: AC02 & AC03 setDefault flow excludes soft-deleted rows and handles customer with zero prior defaults")
    void setDefaultFlow_zeroPriorDefaultsAndDeletedRowExclusion_persistsTargetAsOnlyDefault() {
        Instant initialTimestamp = Instant.parse("2026-11-01T01:00:00Z");
        UserAddressEntity activeNonDefault =
                persistAddress(42L, "Active NonDefault", false, initialTimestamp, initialTimestamp);
        UserAddressEntity softDeleted =
                persistAddress(42L, "Soft Deleted", false, initialTimestamp, initialTimestamp);
        softDeleted.softDelete(Instant.parse("2026-11-11T01:00:00Z"));
        userAddressRepository.saveAndFlush(softDeleted);
        entityManager.clear();

        assertThat(
                        userAddressRepository.findByAddressIdAndCustomerIdAndIsDeletedFalse(
                                softDeleted.getAddressId(), 42L))
                .isEmpty();

        UserAddressEntity loaded =
                userAddressRepository
                        .findByAddressIdAndCustomerIdAndIsDeletedFalse(
                                activeNonDefault.getAddressId(), 42L)
                        .orElseThrow();
        int clearedCount = userAddressRepository.clearDefaultByCustomerId(42L);
        loaded.markDefault();
        UserAddressEntity saved = userAddressRepository.saveAndFlush(loaded);
        entityManager.clear();

        assertThat(clearedCount).isZero();
        assertThat(saved.isDefault()).isTrue();

        List<UserAddressEntity> ordered = userAddressRepository.findAllByCustomerIdOrdered(42L);
        assertThat(ordered).hasSize(1);
        assertThat(ordered.getFirst().getAddressId()).isEqualTo(activeNonDefault.getAddressId());
        assertThat(ordered.getFirst().isDefault()).isTrue();
    }
}
