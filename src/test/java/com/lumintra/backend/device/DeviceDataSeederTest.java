package com.lumintra.backend.device;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.bootstrap.DeviceDataSeeder;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DeviceDataSeederTest {

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private DeviceDataSeeder seeder;

    @Captor
    private ArgumentCaptor<LedInstanceEntity> instanceCaptor;

    @Test
    @DisplayName("DEV-173 SEED-01 [N]: Seeds 6 representative LED instances across all lifecycle statuses idempotently")
    void run_cleanDatabaseWithModelsAndCustomer_seedsAllStatuses() {
        LedModelEntity pro = LedModelEntity.createForProduct(101L, "Matrix 64x64 Pro", Map.of(), 50L);
        LedModelEntity compact = LedModelEntity.createForProduct(102L, "Matrix 32x64", Map.of(), 30L);

        given(ledModelRepository.findAll(any(Sort.class))).willReturn(List.of(pro, compact));
        given(customerLookupService.findFirstProfile()).willReturn(
                Optional.of(new CustomerProfileSnapshot(42L, "Nguyen Van A", "0901234567", "customer@example.com")));
        given(ledInstanceRepository.existsBySerialNumber(anyString())).willReturn(false);

        AtomicLong ids = new AtomicLong(1L);
        given(ledInstanceRepository.save(any(LedInstanceEntity.class))).willAnswer(inv -> {
            LedInstanceEntity saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "instanceId", ids.getAndIncrement());
            return saved;
        });

        seeder.run(applicationArguments);

        verify(ledInstanceRepository, times(6)).save(instanceCaptor.capture());
        List<LedInstanceEntity> saved = instanceCaptor.getAllValues();
        assertThat(saved)
                .extracting(LedInstanceEntity::getStatus)
                .containsExactly(
                        LedInstanceStatus.IN_STOCK,
                        LedInstanceStatus.IN_STOCK,
                        LedInstanceStatus.IN_TRANSIT,
                        LedInstanceStatus.BOUND,
                        LedInstanceStatus.RETURNED,
                        LedInstanceStatus.DECOMMISSIONED
                );
        LedInstanceEntity bound = saved.get(3);
        assertThat(bound.getOwnerCustomerId()).isEqualTo(42L);
        assertThat(bound.getBoundAt()).isNotNull();
        assertThat(bound.getWarrantyExpiresAt()).isAfter(bound.getBoundAt());
    }

    @Test
    @DisplayName("DEV-173 SEED-02 [B]: Skips seeding safely when no LED models exist or when records already exist")
    void run_missingModelsOrExistingInstances_handlesIdempotently() {
        given(ledModelRepository.findAll(any(Sort.class))).willReturn(List.of());

        seeder.run(applicationArguments);

        verifyNoInteractions(ledInstanceRepository);

        LedModelEntity pro = LedModelEntity.createForProduct(101L, "Matrix 64x64 Pro", Map.of(), 50L);
        given(ledModelRepository.findAll(any(Sort.class))).willReturn(List.of(pro));
        given(customerLookupService.findFirstProfile()).willReturn(Optional.empty());
        given(ledInstanceRepository.existsBySerialNumber(anyString())).willReturn(true);

        seeder.run(applicationArguments);

        verify(ledInstanceRepository, never()).save(any());
    }
}
