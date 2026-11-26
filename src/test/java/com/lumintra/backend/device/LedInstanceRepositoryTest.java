package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:led_instance_repo_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@ContextConfiguration(classes = LedInstanceRepositoryTest.Config.class)
class LedInstanceRepositoryTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LedInstanceEntity.class)
    @EnableJpaRepositories(basePackageClasses = LedInstanceRepository.class)
    static class Config {}

    @Autowired
    private LedInstanceRepository ledInstanceRepository;

    @BeforeEach
    void setUp() {
        ledInstanceRepository.deleteAll();
    }

    @Test
    @DisplayName("DEV-94 REPO-01 [N]: Paginates customer owned devices ordered by boundAt DESC NULLS LAST, instanceId DESC")
    void findAllByOwnerCustomerIdOrderByBoundAtDesc_filtersByOwnerAndPaginatesNewestFirst() {
        Instant manufacturedAt = Instant.parse("2026-10-01T00:00:00Z");
        Instant olderBoundAt = Instant.parse("2026-11-10T08:00:00Z");
        Instant newerBoundAt = Instant.parse("2026-11-14T09:30:00Z");

        LedInstanceEntity nullBoundOwned = ledInstanceRepository.saveAndFlush(LedInstanceEntity.createWithState(
                101L,
                "SN-REPO-0000",
                "AA:BB:CC:00:01:00",
                42L,
                "Legacy Unset Bound Timestamp",
                LedInstanceStatus.RETURNED,
                manufacturedAt,
                null,
                null
        ));

        LedInstanceEntity olderOwned = ledInstanceRepository.saveAndFlush(LedInstanceEntity.createWithState(
                101L,
                "SN-REPO-0001",
                "AA:BB:CC:00:01:01",
                42L,
                "Older Rear Matrix",
                LedInstanceStatus.BOUND,
                manufacturedAt,
                olderBoundAt,
                olderBoundAt.plusSeconds(86400)
        ));

        LedInstanceEntity newerOwnedFirst = ledInstanceRepository.saveAndFlush(LedInstanceEntity.createWithState(
                101L,
                "SN-REPO-0002",
                "AA:BB:CC:00:01:02",
                42L,
                "Newer Front Matrix A",
                LedInstanceStatus.BOUND,
                manufacturedAt,
                newerBoundAt,
                newerBoundAt.plusSeconds(86400)
        ));

        LedInstanceEntity newerOwnedTieBreaker = ledInstanceRepository.saveAndFlush(LedInstanceEntity.createWithState(
                102L,
                "SN-REPO-0003",
                "AA:BB:CC:00:01:03",
                42L,
                "Newer Front Matrix B",
                LedInstanceStatus.BOUND,
                manufacturedAt,
                newerBoundAt,
                newerBoundAt.plusSeconds(86400)
        ));

        ledInstanceRepository.saveAndFlush(LedInstanceEntity.createWithState(
                101L,
                "SN-REPO-0004",
                "AA:BB:CC:00:01:04",
                99L,
                "Other Customer Device",
                LedInstanceStatus.BOUND,
                manufacturedAt,
                newerBoundAt.plusSeconds(3600),
                newerBoundAt.plusSeconds(90000)
        ));

        ledInstanceRepository.saveAndFlush(LedInstanceEntity.createInStock(
                101L,
                "SN-REPO-0005",
                "AA:BB:CC:00:01:05",
                manufacturedAt
        ));

        Page<LedInstanceEntity> firstPage = ledInstanceRepository
                .findAllByOwnerCustomerIdOrderByBoundAtDesc(42L, PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(4L);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.hasPrevious()).isFalse();
        assertThat(firstPage.getContent())
                .extracting(LedInstanceEntity::getInstanceId)
                .containsExactly(
                        newerOwnedTieBreaker.getInstanceId(),
                        newerOwnedFirst.getInstanceId()
                );

        Page<LedInstanceEntity> secondPage = ledInstanceRepository
                .findAllByOwnerCustomerIdOrderByBoundAtDesc(42L, PageRequest.of(1, 2));

        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.hasPrevious()).isTrue();
        assertThat(secondPage.getContent())
                .extracting(LedInstanceEntity::getInstanceId)
                .containsExactly(
                        olderOwned.getInstanceId(),
                        nullBoundOwned.getInstanceId()
                );

        Page<LedInstanceEntity> emptyCustomerPage = ledInstanceRepository
                .findAllByOwnerCustomerIdOrderByBoundAtDesc(777L, PageRequest.of(0, 20));
        assertThat(emptyCustomerPage.getContent()).isEmpty();
        assertThat(emptyCustomerPage.getTotalElements()).isZero();
    }
}
