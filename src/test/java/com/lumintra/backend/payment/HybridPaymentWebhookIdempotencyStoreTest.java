package com.lumintra.backend.payment;

import com.lumintra.backend.payment.infrastructure.persistence.HybridPaymentWebhookIdempotencyStore;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridPaymentWebhookIdempotencyStoreTest {

    @Mock
    private SafeCacheService safeCacheService;

    private HybridPaymentWebhookIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new HybridPaymentWebhookIdempotencyStore(safeCacheService);
    }

    @Test
    @DisplayName("PAY-02 [N]: Stores and reads sepay_tx_id idempotency key from Redis")
    void markProcessedAndIsProcessed_redisAvailable_usesRedisKey() {
        when(safeCacheService.hasKey("payment:webhook:sepay:92704")).thenReturn(false, true);

        assertThat(store.isProcessed(92704L)).isFalse();

        store.markProcessed(92704L, "LMTORD102A1B2C3D4", Duration.ofDays(7));

        verify(safeCacheService).set(
                eq("payment:webhook:sepay:92704"),
                eq("LMTORD102A1B2C3D4"),
                eq(Duration.ofDays(7))
        );
        assertThat(store.isProcessed(92704L)).isTrue();
    }

    @Test
    @DisplayName("PAY-02 [B]: Falls back to in-memory idempotency store when Redis throws CacheUnavailableException")
    void markProcessedAndIsProcessed_redisUnavailable_usesFallbackStore() {
        RuntimeException rootCause = new RuntimeException("connection refused");
        doThrow(new CacheUnavailableException("Redis down", rootCause))
                .when(safeCacheService)
                .set(eq("payment:webhook:sepay:99001"), eq("LMTORD102A1B2C3D4"), eq(Duration.ofDays(7)));
        when(safeCacheService.hasKey("payment:webhook:sepay:99001"))
                .thenThrow(new CacheUnavailableException("Redis down", rootCause));

        store.markProcessed(99001L, "LMTORD102A1B2C3D4", Duration.ofDays(7));

        assertThat(store.isProcessed(99001L)).isTrue();
        assertThat(store.isProcessed(null)).isFalse();
        assertThat(store.isProcessed(0L)).isFalse();
    }

    @Test
    @DisplayName("PAY-02 [B]: Ignores null or non-positive sepay_tx_id values")
    void invalidSepayTxId_noRedisCalls() {
        store.markProcessed(null, "CODE", Duration.ofDays(7));
        store.markProcessed(-1L, "CODE", Duration.ofDays(7));

        assertThat(store.isProcessed(null)).isFalse();
        assertThat(store.isProcessed(-1L)).isFalse();
        verifyNoInteractions(safeCacheService);
    }
}
