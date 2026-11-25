package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.StockInLedInstancesRequest;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.api.response.StockInLedInstancesResponse;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.device.DuplicateLedInstanceException;
import com.lumintra.backend.shared.exception.device.LedModelDeprecatedException;
import com.lumintra.backend.shared.exception.device.LedModelNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockInLedInstancesUseCaseTest {

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private LedInstanceMapper ledInstanceMapper;

    @InjectMocks
    private StockInLedInstancesUseCase useCase;

    @Test
    @DisplayName("N: Stock-in registers IN_STOCK LED instances and increments model stockQuantity and sets ACTIVE")
    void execute_validBatch_savesInstancesAndIncrementsStock() {
        LedModelEntity model = LedModelEntity.createCatalogModel(
                1L, 10L, "Lumintra Matrix 64x64 Pro", 64, 64, Map.of(), 0L, LedModelStatus.OUT_OF_STOCK);

        given(ledModelRepository.findById(1L)).willReturn(Optional.of(model));
        given(ledInstanceRepository.findBySerialNumberIn(anyCollection())).willReturn(List.of());
        given(ledInstanceRepository.findByMacAddressIn(anyCollection())).willReturn(List.of());

        AtomicLong idGen = new AtomicLong(100L);
        given(ledInstanceRepository.saveAll(anyList())).willAnswer(inv -> {
            List<LedInstanceEntity> entities = inv.getArgument(0);
            entities.forEach(e -> ReflectionTestUtils.setField(e, "instanceId", idGen.getAndIncrement()));
            return entities;
        });
        given(ledModelRepository.save(any(LedModelEntity.class))).willAnswer(inv -> inv.getArgument(0));
        given(ledInstanceMapper.toItem(any(), anyMap(), anyMap(), anyMap())).willAnswer(inv -> {
            LedInstanceEntity e = inv.getArgument(0);
            return LedInstanceListResponse.LedInstanceItem.builder()
                    .instanceId(e.getInstanceId())
                    .modelId(e.getModelId())
                    .serialNumber(e.getSerialNumber())
                    .macAddress(e.getMacAddress())
                    .status(e.getStatus())
                    .qrPayload("{\"sn\":\"" + e.getSerialNumber() + "\"}")
                    .build();
        });

        StockInLedInstancesRequest request = new StockInLedInstancesRequest(
                1L,
                List.of(
                        new StockInLedInstancesRequest.StockInLedInstanceItem("LMT-64X64-NEW-0001", "aa:bb:cc:dd:ee:01", Instant.now()),
                        new StockInLedInstancesRequest.StockInLedInstanceItem("LMT-64X64-NEW-0002", "aa:bb:cc:dd:ee:02", null)
                )
        );

        StockInLedInstancesResponse response = useCase.execute(request);

        assertThat(response.importedCount()).isEqualTo(2);
        assertThat(response.stockQuantity()).isEqualTo(2L);
        assertThat(response.modelStatus()).isEqualTo(LedModelStatus.ACTIVE);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).allSatisfy(i -> assertThat(i.status()).isEqualTo(LedInstanceStatus.IN_STOCK));
        verify(ledModelRepository).save(model);
    }

    @Test
    @DisplayName("A: Throws DuplicateLedInstanceException when serial already exists in DB")
    void execute_duplicateSerialInDb_throwsConflict() {
        LedModelEntity model = LedModelEntity.createCatalogModel(
                1L, 10L, "Lumintra Matrix 64x64 Pro", 64, 64, Map.of(), 10L, LedModelStatus.ACTIVE);
        given(ledModelRepository.findById(1L)).willReturn(Optional.of(model));
        given(ledInstanceRepository.findBySerialNumberIn(anyCollection()))
                .willReturn(List.of(LedInstanceEntity.createInStock(1L, "LMT-DUP-0001", "AA:BB:CC:DD:EE:01", Instant.now())));

        StockInLedInstancesRequest request = new StockInLedInstancesRequest(
                1L,
                List.of(new StockInLedInstancesRequest.StockInLedInstanceItem("LMT-DUP-0001", "AA:BB:CC:DD:EE:99", null))
        );

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(DuplicateLedInstanceException.class)
                .hasMessageContaining("LMT-DUP-0001");
    }

    @Test
    @DisplayName("A: Throws LedModelDeprecatedException when model is DEPRECATED")
    void execute_deprecatedModel_throwsConflict() {
        LedModelEntity model = LedModelEntity.createCatalogModel(
                1L, 10L, "Old Model", 32, 32, Map.of(), 0L, LedModelStatus.DEPRECATED);
        given(ledModelRepository.findById(1L)).willReturn(Optional.of(model));

        StockInLedInstancesRequest request = new StockInLedInstancesRequest(
                1L,
                List.of(new StockInLedInstancesRequest.StockInLedInstanceItem("LMT-001", "AA:BB:CC:DD:EE:01", null))
        );

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(LedModelDeprecatedException.class);
    }

    @Test
    @DisplayName("A: Throws LedModelNotFoundException when model does not exist")
    void execute_missingModel_throwsNotFound() {
        given(ledModelRepository.findById(999L)).willReturn(Optional.empty());

        StockInLedInstancesRequest request = new StockInLedInstancesRequest(
                999L,
                List.of(new StockInLedInstancesRequest.StockInLedInstanceItem("LMT-001", "AA:BB:CC:DD:EE:01", null))
        );

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(LedModelNotFoundException.class);
    }
}
