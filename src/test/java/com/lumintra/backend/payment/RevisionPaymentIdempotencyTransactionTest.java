package com.lumintra.backend.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lumintra.backend.payment.infrastructure.persistence.HybridPaymentWebhookIdempotencyStore;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

@ExtendWith(MockitoExtension.class)
class RevisionPaymentIdempotencyTransactionTest {
    @Mock SafeCacheService cache;
    HybridPaymentWebhookIdempotencyStore store;
    TransactionTemplate transaction;

    @BeforeEach
    void setup() {
        store = new HybridPaymentWebhookIdempotencyStore(cache);
        transaction =
                new TransactionTemplate(
                        new AbstractPlatformTransactionManager() {
                            @Override
                            protected Object doGetTransaction() {
                                return new Object();
                            }

                            @Override
                            protected void doBegin(Object tx, TransactionDefinition definition) {}

                            @Override
                            protected void doCommit(DefaultTransactionStatus status) {}

                            @Override
                            protected void doRollback(DefaultTransactionStatus status) {}
                        });
    }

    @Test
    void recordsIdempotencyOnlyAfterCommit() {
        transaction.executeWithoutResult(
                status -> {
                    store.markProcessed(1234L, "REV105", Duration.ofDays(7));
                    verifyNoInteractions(cache);
                    assertThat(store.isProcessed(1234L)).isFalse();
                });
        verify(cache).set("payment:webhook:sepay:1234", "REV105", Duration.ofDays(7));
        assertThat(store.isProcessed(1234L)).isTrue();
    }

    @Test
    void leavesEventRetryableWhenListenerThrows() {
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            store.markProcessed(
                                                    1234L, "REV105", Duration.ofDays(7));
                                            throw new IllegalStateException(
                                                    "Revision listener failed");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cache);
        assertThat(store.isProcessed(1234L)).isFalse();
        verify(cache, never()).set(anyString(), any(), any());
    }

    @Test
    void doesNotRecordForRollbackOnlyTransaction() {
        transaction.executeWithoutResult(
                status -> {
                    store.markProcessed(1234L, "REV105", Duration.ofDays(7));
                    status.setRollbackOnly();
                });
        verifyNoInteractions(cache);
        assertThat(store.isProcessed(1234L)).isFalse();
    }

    @Test
    void keepsRedisOutageFallbackAfterCommit() {
        doThrow(new CacheUnavailableException("Redis down", null))
                .when(cache)
                .set("payment:webhook:sepay:1234", "REV105", Duration.ofDays(7));
        when(cache.hasKey("payment:webhook:sepay:1234"))
                .thenThrow(new CacheUnavailableException("Redis down", null));
        transaction.executeWithoutResult(
                status -> store.markProcessed(1234L, "REV105", Duration.ofDays(7)));
        assertThat(store.isProcessed(1234L)).isTrue();
    }
}
