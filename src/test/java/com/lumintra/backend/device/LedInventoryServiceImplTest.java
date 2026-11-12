package com.lumintra.backend.device;

import com.lumintra.backend.device.infrastructure.persistence.LedInventoryServiceImpl;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedInventoryServiceImplTest {

    @Mock
    private LedModelRepository ledModelRepository;

    private LedInventoryServiceImpl ledInventoryService;

    @BeforeEach
    void setUp() {
        ledInventoryService = new LedInventoryServiceImpl(ledModelRepository);
    }

    @Test
    @DisplayName("deductStocksIfAvailable deducts models in ascending ID order when all have sufficient stock")
    void deductStocksIfAvailable_allModelsAvailable_deductsInAscendingOrder() {
        when(ledModelRepository.deductStockIfAvailable(101L, 2L)).thenReturn(1);
        when(ledModelRepository.deductStockIfAvailable(205L, 1L)).thenReturn(1);

        boolean ok = ledInventoryService.deductStocksIfAvailable(Map.of(205L, 1L, 101L, 2L));
        assertThat(ok).isTrue();

        InOrder inOrder = inOrder(ledModelRepository);
        inOrder.verify(ledModelRepository).deductStockIfAvailable(101L, 2L);
        inOrder.verify(ledModelRepository).deductStockIfAvailable(205L, 1L);
    }

    @Test
    @DisplayName("deductStocksIfAvailable aborts immediately and returns false when any model lacks stock")
    void deductStocksIfAvailable_insufficientStock_abortsAndReturnsFalse() {
        when(ledModelRepository.deductStockIfAvailable(101L, 5L)).thenReturn(0);

        boolean insufficient = ledInventoryService.deductStocksIfAvailable(Map.of(101L, 5L, 205L, 1L));
        assertThat(insufficient).isFalse();
        verify(ledModelRepository, never()).deductStockIfAvailable(205L, 1L);
    }

    @Test
    @DisplayName("COM-12 UTCID01 [N]: restoreStocks increments hardware models in ascending ID order")
    void restoreStocks_multipleModels_restoresInAscendingOrder() {
        when(ledModelRepository.restoreStock(101L, 3L)).thenReturn(1);
        when(ledModelRepository.restoreStock(205L, 2L)).thenReturn(1);

        ledInventoryService.restoreStocks(Map.of(205L, 2L, 101L, 3L));

        InOrder inOrder = inOrder(ledModelRepository);
        inOrder.verify(ledModelRepository).restoreStock(101L, 3L);
        inOrder.verify(ledModelRepository).restoreStock(205L, 2L);
        verifyNoMoreInteractions(ledModelRepository);
    }

    @Test
    @DisplayName("COM-12 UTCID02 [B]: restoreStocks skips empty/invalid entries and continues cleanly when a model row is missing")
    void restoreStocks_emptyOrMissingModel_handlesGracefully() {
        ledInventoryService.restoreStocks(null);
        ledInventoryService.restoreStocks(Map.of());

        Map<Long, Long> invalidEntries = new HashMap<>();
        invalidEntries.put(null, 5L);
        invalidEntries.put(101L, 0L);
        invalidEntries.put(102L, -2L);
        invalidEntries.put(103L, null);
        ledInventoryService.restoreStocks(invalidEntries);

        verifyNoInteractions(ledModelRepository);

        when(ledModelRepository.restoreStock(101L, 2L)).thenReturn(0);
        when(ledModelRepository.restoreStock(205L, 1L)).thenReturn(1);

        ledInventoryService.restoreStocks(Map.of(101L, 2L, 205L, 1L));

        verify(ledModelRepository).restoreStock(101L, 2L);
        verify(ledModelRepository).restoreStock(205L, 1L);
    }
}
