package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.LedInstanceFilterRequest;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:led_instance_spec_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@ContextConfiguration(classes = LedInstanceSpecificationsPersistenceTest.Config.class)
class LedInstanceSpecificationsPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LedInstanceEntity.class)
    @EnableJpaRepositories(basePackageClasses = LedInstanceRepository.class)
    static class Config {}

    @Autowired
    private LedInstanceRepository repository;

    private Long inStockId;
    private Long inTransitId;
    private Long boundId;
    private Long returnedId;
    private Long decommissionedId;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();

        LedInstanceEntity inStock = repository.saveAndFlush(LedInstanceEntity.createInStock(
                101L, "LMT-64X64-0001", "aa:bb:cc:11:22:01", "REV_1_0", Instant.now()));

        LedInstanceEntity inTransit = repository.saveAndFlush(LedInstanceEntity.createWithState(
                102L, "LMT-32X64-0002", "AA:BB:CC:11:22:02", "REV_1_0",
                null, null, LedInstanceStatus.IN_TRANSIT, Instant.now(), null, null));

        LedInstanceEntity bound = repository.saveAndFlush(LedInstanceEntity.createWithState(
                101L, "LMT-64X64-0003", "AA:BB:CC:11:22:03", "REV_1_0",
                42L, "Car Matrix", LedInstanceStatus.BOUND, Instant.now(), Instant.now(), Instant.now().plusSeconds(86400)));

        LedInstanceEntity returned = repository.saveAndFlush(LedInstanceEntity.createWithState(
                101L, "LMT-64%PRO_0004", "AA:BB:CC:99:88:04", "REV_1_0",
                null, null, LedInstanceStatus.RETURNED, Instant.now(), null, null));

        LedInstanceEntity decommissioned = repository.saveAndFlush(LedInstanceEntity.createWithState(
                102L, "LMT-32X64-0005", "DD:EE:FF:00:11:05", "REV_1_0",
                null, null, LedInstanceStatus.DECOMMISSIONED, Instant.now(), null, null));

        inStockId = inStock.getInstanceId();
        inTransitId = inTransit.getInstanceId();
        boundId = bound.getInstanceId();
        returnedId = returned.getInstanceId();
        decommissionedId = decommissioned.getInstanceId();
    }

    @Test
    @DisplayName("DEV-173 SPEC-01 [N]: Returns all instances paginated and sorted by instanceId DESC")
    void findAll_unfiltered_returnsAllSortedDescWithPagination() {
        Page<LedInstanceEntity> firstPage = repository.findAll(
                LedInstanceSpecifications.withFilter(new LedInstanceFilterRequest(0, 3, null, null, null)),
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "instanceId"))
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(LedInstanceEntity::getInstanceId)
                .containsExactly(decommissionedId, returnedId, boundId);
    }

    @Test
    @DisplayName("DEV-173 SPEC-02 [N]: Filters by modelId and status individually and combined")
    void findAll_modelIdAndStatus_filtersCorrectly() {
        List<LedInstanceEntity> model101 = repository.findAll(
                LedInstanceSpecifications.withFilter(new LedInstanceFilterRequest(0, 20, 101L, null, null)),
                Sort.by(Sort.Direction.DESC, "instanceId")
        );
        assertThat(model101)
                .extracting(LedInstanceEntity::getInstanceId)
                .containsExactly(returnedId, boundId, inStockId);

        List<LedInstanceEntity> transit = repository.findAll(
                LedInstanceSpecifications.withFilter(
                        new LedInstanceFilterRequest(0, 20, 102L, LedInstanceStatus.IN_TRANSIT, null))
        );
        assertThat(transit)
                .extracting(LedInstanceEntity::getInstanceId)
                .containsExactly(inTransitId);
    }

    @Test
    @DisplayName("DEV-173 SPEC-03 [N/B]: Search performs case-insensitive literal substring match across serialNumber and macAddress")
    void findAll_search_matchesSerialAndMacAndEscapesLikeWildcards() {
        List<LedInstanceEntity> bySerial = repository.findAll(
                LedInstanceSpecifications.withFilter(
                        new LedInstanceFilterRequest(0, 20, null, null, "32x64")),
                Sort.by(Sort.Direction.DESC, "instanceId")
        );
        assertThat(bySerial)
                .extracting(LedInstanceEntity::getInstanceId)
                .containsExactly(decommissionedId, inTransitId);

        List<LedInstanceEntity> byMac = repository.findAll(
                LedInstanceSpecifications.withFilter(
                        new LedInstanceFilterRequest(0, 20, null, null, "cc:99:88"))
        );
        assertThat(byMac)
                .extracting(LedInstanceEntity::getInstanceId)
                .containsExactly(returnedId);

        List<LedInstanceEntity> escapedWildcard = repository.findAll(
                LedInstanceSpecifications.withFilter(
                        new LedInstanceFilterRequest(0, 20, null, null, "64%pro_"))
        );
        assertThat(escapedWildcard)
                .extracting(LedInstanceEntity::getInstanceId)
                .containsExactly(returnedId);
    }
}
