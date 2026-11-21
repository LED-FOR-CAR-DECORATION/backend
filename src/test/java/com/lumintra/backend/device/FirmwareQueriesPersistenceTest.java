package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:firmware_queries_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=CHARACTER;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@ContextConfiguration(classes = FirmwareQueriesPersistenceTest.Config.class)
class FirmwareQueriesPersistenceTest {

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LedModelEntity.class)
    @EnableJpaRepositories(basePackageClasses = LedModelRepository.class)
    static class Config {}

    @Autowired
    private FirmwareVersionRepository firmwareRepository;

    @Autowired
    private LedInstanceRepository instanceRepository;

    private FirmwareVersionEntity saveFirmware(long modelId, String version, FirmwareStatus status) {
        return firmwareRepository.save(FirmwareVersionEntity.create(modelId, version, "notes",
                "firmware/" + modelId + "/" + version + ".bin", 2048L, "c".repeat(64), status, 1L, Instant.now()));
    }

    private void saveInstance(String serial, String mac, Long firmwareId, LedInstanceStatus status) {
        LedInstanceEntity instance = LedInstanceEntity.createWithState(
                1L, serial, mac, null, null, null, status, Instant.now(), null, null);
        instance.setCurrentFirmwareId(firmwareId);
        instanceRepository.save(instance);
    }

    @Test
    @DisplayName("#284 Persistence [N]: Active releases are fetched for the given models only")
    void findByModelIdInAndStatus_returnsOnlyActiveReleasesOfRequestedModels() {
        FirmwareVersionEntity activeOne = saveFirmware(1L, "1.1.0", FirmwareStatus.ACTIVE);
        saveFirmware(1L, "1.0.0", FirmwareStatus.SUPERSEDED);
        FirmwareVersionEntity activeTwo = saveFirmware(2L, "2.0.0", FirmwareStatus.ACTIVE);
        saveFirmware(3L, "3.0.0", FirmwareStatus.ACTIVE);
        saveFirmware(2L, "2.1.0", FirmwareStatus.DEPRECATED);

        List<FirmwareVersionEntity> result = firmwareRepository.findByModelIdInAndStatus(List.of(1L, 2L), FirmwareStatus.ACTIVE);

        assertThat(result).extracting(FirmwareVersionEntity::getFirmwareId)
                .containsExactlyInAnyOrder(activeOne.getFirmwareId(), activeTwo.getFirmwareId());
    }

    @Autowired
    private LedModelRepository modelRepository;

    @Test
    @DisplayName("DEV-12 Persistence [N]: findByModelId returns every release of the model and the row lock query loads the model")
    void findByModelId_andLockedModelLookup() {
        LedModelEntity model = modelRepository.save(LedModelEntity.createStandalone(
                "Firmware Lock Model", 64, 64, Map.of("widthPx", 64, "heightPx", 64), 5L));
        saveFirmware(model.getModelId(), "1.0.0", FirmwareStatus.SUPERSEDED);
        saveFirmware(model.getModelId(), "1.1.0", FirmwareStatus.DEPRECATED);
        saveFirmware(model.getModelId(), "1.2.0", FirmwareStatus.ACTIVE);
        saveFirmware(model.getModelId() + 1000, "9.0.0", FirmwareStatus.ACTIVE);

        assertThat(firmwareRepository.findByModelId(model.getModelId()))
                .extracting(FirmwareVersionEntity::getVersion)
                .containsExactlyInAnyOrder("1.0.0", "1.1.0", "1.2.0");
        assertThat(modelRepository.findByIdForUpdate(model.getModelId()))
                .get().extracting(LedModelEntity::getModelName).isEqualTo("Firmware Lock Model");
        assertThat(modelRepository.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    @DisplayName("DEV-12 Persistence [A]: The same version cannot be stored twice for one model")
    void uniqueModelVersion_isEnforced() {
        saveFirmware(77L, "1.0.0", FirmwareStatus.ACTIVE);
        firmwareRepository.flush();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            saveFirmware(77L, "1.0.0", FirmwareStatus.ACTIVE);
            firmwareRepository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("#284 Persistence [N]: Installed counts group BOUND devices per firmware and ignore other statuses")
    void countBoundDevicesByFirmwareIds_countsOnlyBoundDevices() {
        Long a = saveFirmware(1L, "1.0.0", FirmwareStatus.ACTIVE).getFirmwareId();
        Long b = saveFirmware(2L, "2.0.0", FirmwareStatus.ACTIVE).getFirmwareId();
        Long unrelated = saveFirmware(3L, "3.0.0", FirmwareStatus.ACTIVE).getFirmwareId();
        saveInstance("SN-1", "AA:BB:CC:00:00:01", a, LedInstanceStatus.BOUND);
        saveInstance("SN-2", "AA:BB:CC:00:00:02", a, LedInstanceStatus.BOUND);
        saveInstance("SN-3", "AA:BB:CC:00:00:03", a, LedInstanceStatus.DECOMMISSIONED);
        saveInstance("SN-4", "AA:BB:CC:00:00:04", b, LedInstanceStatus.IN_STOCK);
        saveInstance("SN-5", "AA:BB:CC:00:00:05", unrelated, LedInstanceStatus.BOUND);
        saveInstance("SN-6", "AA:BB:CC:00:00:06", null, LedInstanceStatus.BOUND);

        Map<Long, Long> counts = instanceRepository.countBoundDevicesByFirmwareIds(List.of(a, b)).stream()
                .collect(Collectors.toMap(
                        LedInstanceRepository.FirmwareInstallCount::getFirmwareId,
                        LedInstanceRepository.FirmwareInstallCount::getDeviceCount));

        assertThat(counts).containsExactly(Map.entry(a, 2L));
    }
}
