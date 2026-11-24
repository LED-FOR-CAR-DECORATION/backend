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
    private com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService orderFulfillmentLookupService;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private DeviceDataSeeder seeder;

    @Captor
    private ArgumentCaptor<LedInstanceEntity> instanceCaptor;

    @Test
    @DisplayName("DEV-173 SEED-01 [N]: Seeds 8 LED instances for primary customer (including clean active & expired warranty) and 13 when extra customers exist")
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

        verify(ledInstanceRepository, times(8)).save(instanceCaptor.capture());
        List<LedInstanceEntity> saved = instanceCaptor.getAllValues();
        assertThat(saved)
                .extracting(LedInstanceEntity::getStatus)
                .containsExactly(
                        LedInstanceStatus.IN_STOCK,
                        LedInstanceStatus.IN_STOCK,
                        LedInstanceStatus.IN_TRANSIT,
                        LedInstanceStatus.BOUND,
                        LedInstanceStatus.RETURNED,
                        LedInstanceStatus.DECOMMISSIONED,
                        LedInstanceStatus.BOUND,
                        LedInstanceStatus.BOUND
                );
        LedInstanceEntity bound = saved.get(3);
        assertThat(bound.getOwnerCustomerId()).isEqualTo(42L);
        assertThat(bound.getBoundAt()).isNotNull();
        assertThat(bound.getWarrantyExpiresAt()).isAfter(bound.getBoundAt());

        LedInstanceEntity cleanWarranty = saved.get(6);
        assertThat(cleanWarranty.getSerialNumber()).isEqualTo(DeviceDataSeeder.SEED_SERIAL_WARRANTY_CLEAN);
        assertThat(cleanWarranty.getOwnerCustomerId()).isEqualTo(42L);
        assertThat(cleanWarranty.getWarrantyExpiresAt()).isAfter(java.time.Instant.now());

        LedInstanceEntity expiredWarranty = saved.get(7);
        assertThat(expiredWarranty.getSerialNumber()).isEqualTo(DeviceDataSeeder.SEED_SERIAL_WARRANTY_EXPIRED);
        assertThat(expiredWarranty.getOwnerCustomerId()).isEqualTo(42L);
        assertThat(expiredWarranty.getWarrantyExpiresAt()).isBefore(java.time.Instant.now());
    }

    @Test
    @DisplayName("N: Seeds 13 LED instances across all customers when multi-customer seeder profiles exist")
    void run_withExtraCustomerProfiles_seedsAll13Instances() {
        LedModelEntity pro = LedModelEntity.createForProduct(101L, "Matrix 64x64 Pro", Map.of(), 50L);
        LedModelEntity compact = LedModelEntity.createForProduct(102L, "Matrix 32x64", Map.of(), 30L);

        given(ledModelRepository.findAll(any(Sort.class))).willReturn(List.of(pro, compact));
        given(customerLookupService.findFirstProfile()).willReturn(
                Optional.of(new CustomerProfileSnapshot(42L, "Nguyen Van A", "0901234567", "customer@example.com")));
        given(customerLookupService.findProfileByEmail("minhanh.nguyen@gmail.com")).willReturn(
                Optional.of(new CustomerProfileSnapshot(43L, "Nguyễn Minh Anh", "0902345678", "minhanh.nguyen@gmail.com")));
        given(customerLookupService.findProfileByEmail("quocbao.tran@gmail.com")).willReturn(
                Optional.of(new CustomerProfileSnapshot(44L, "Trần Quốc Bảo", "0903456789", "quocbao.tran@gmail.com")));
        given(customerLookupService.findProfileByEmail("thaovy.le@gmail.com")).willReturn(
                Optional.of(new CustomerProfileSnapshot(45L, "Lê Thảo Vy", "0904567890", "thaovy.le@gmail.com")));
        given(ledInstanceRepository.existsBySerialNumber(anyString())).willReturn(false);

        AtomicLong ids = new AtomicLong(1L);
        given(ledInstanceRepository.save(any(LedInstanceEntity.class))).willAnswer(inv -> {
            LedInstanceEntity saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "instanceId", ids.getAndIncrement());
            return saved;
        });

        seeder.run(applicationArguments);

        verify(ledInstanceRepository, times(13)).save(instanceCaptor.capture());
        assertThat(instanceCaptor.getAllValues().stream()
                .filter(instance -> instance.getStatus() == LedInstanceStatus.BOUND)
                .map(LedInstanceEntity::getDeviceName))
                .allSatisfy(name -> assertThat(name).isIn("Matrix 64x64 Pro", "Matrix 32x64"));
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
