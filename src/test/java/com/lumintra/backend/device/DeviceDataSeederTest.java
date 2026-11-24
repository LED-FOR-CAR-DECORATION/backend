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
    @DisplayName("DEV-173 SEED-01 [N]: Seeds 10 IN_STOCK instances per LED model plus lifecycle/warranty units and syncs stockQuantity")
    void run_cleanDatabaseWithModelsAndCustomer_seedsAllStatuses() {
        LedModelEntity pro = LedModelEntity.createForProduct(101L, "Matrix 64x64 Pro", Map.of(), 50L);
        LedModelEntity compact = LedModelEntity.createForProduct(102L, "Matrix 32x64", Map.of(), 30L);

        given(ledModelRepository.findAll(any(Sort.class))).willReturn(List.of(pro, compact));
        given(customerLookupService.findFirstProfile()).willReturn(
                Optional.of(new CustomerProfileSnapshot(42L, "Nguyen Van A", "0901234567", "customer@example.com")));
        given(ledInstanceRepository.existsBySerialNumber(anyString())).willReturn(false);
        given(ledInstanceRepository.countByModelIdAndStatus(101L, LedInstanceStatus.IN_STOCK)).willReturn(10L);
        given(ledInstanceRepository.countByModelIdAndStatus(102L, LedInstanceStatus.IN_STOCK)).willReturn(10L);

        AtomicLong ids = new AtomicLong(1L);
        given(ledInstanceRepository.save(any(LedInstanceEntity.class))).willAnswer(inv -> {
            LedInstanceEntity saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "instanceId", ids.getAndIncrement());
            return saved;
        });

        seeder.run(applicationArguments);

        // 8 base + warranty instances for primary customer + 8 more IN_STOCK for pro + 10 IN_STOCK for compact = 26
        verify(ledInstanceRepository, times(26)).save(instanceCaptor.capture());
        List<LedInstanceEntity> saved = instanceCaptor.getAllValues();

        long proInStock = saved.stream()
                .filter(i -> Long.valueOf(101L).equals(i.getModelId()) && i.getStatus() == LedInstanceStatus.IN_STOCK)
                .count();
        long compactInStock = saved.stream()
                .filter(i -> Long.valueOf(102L).equals(i.getModelId()) && i.getStatus() == LedInstanceStatus.IN_STOCK)
                .count();
        assertThat(proInStock).isEqualTo(10L);
        assertThat(compactInStock).isEqualTo(10L);

        assertThat(pro.getStockQuantity()).isEqualTo(10L);
        assertThat(compact.getStockQuantity()).isEqualTo(10L);
        verify(ledModelRepository, times(2)).save(any(LedModelEntity.class));

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
    @DisplayName("N: Seeds 31 LED instances (10 IN_STOCK per model + 11 lifecycle/multi-customer warranty units) when extra customer profiles exist")
    void run_withExtraCustomerProfiles_seedsAll13Instances() {
        LedModelEntity pro = LedModelEntity.createForProduct(101L, "Matrix 64x64 Pro", Map.of(), 10L);
        LedModelEntity compact = LedModelEntity.createForProduct(102L, "Matrix 32x64", Map.of(), 10L);

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
        given(ledInstanceRepository.countByModelIdAndStatus(101L, LedInstanceStatus.IN_STOCK)).willReturn(10L);
        given(ledInstanceRepository.countByModelIdAndStatus(102L, LedInstanceStatus.IN_STOCK)).willReturn(10L);

        AtomicLong ids = new AtomicLong(1L);
        given(ledInstanceRepository.save(any(LedInstanceEntity.class))).willAnswer(inv -> {
            LedInstanceEntity saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "instanceId", ids.getAndIncrement());
            return saved;
        });

        seeder.run(applicationArguments);

        // 13 base + multi-customer warranty instances + 8 more IN_STOCK for pro + 10 IN_STOCK for compact = 31
        verify(ledInstanceRepository, times(31)).save(instanceCaptor.capture());
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

        LedModelEntity pro = LedModelEntity.createForProduct(101L, "Matrix 64x64 Pro", Map.of(), 10L);
        given(ledModelRepository.findAll(any(Sort.class))).willReturn(List.of(pro));
        given(customerLookupService.findFirstProfile()).willReturn(Optional.empty());
        given(ledInstanceRepository.existsBySerialNumber(anyString())).willReturn(true);
        given(ledInstanceRepository.countByModelIdAndStatus(101L, LedInstanceStatus.IN_STOCK)).willReturn(10L);

        seeder.run(applicationArguments);

        verify(ledInstanceRepository, never()).save(any());
    }
}
