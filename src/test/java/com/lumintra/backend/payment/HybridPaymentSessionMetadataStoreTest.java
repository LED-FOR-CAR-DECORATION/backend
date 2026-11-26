package com.lumintra.backend.payment;

import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.infrastructure.persistence.HybridPaymentSessionMetadataStore;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridPaymentSessionMetadataStoreTest {

    @Mock
    private SafeCacheService safeCacheService;

    private HybridPaymentSessionMetadataStore store;

    @BeforeEach
    void setUp() {
        store = new HybridPaymentSessionMetadataStore(safeCacheService);
    }

    @Test
    @DisplayName("PAY-01 [N]: Saves metadata to Redis and deserializes on cache hit")
    void saveAndFind_redisAvailable_roundTripsMetadata() {
        Instant expiresAt = Instant.parse("2026-11-12T17:00:00Z");
        PaymentSessionMetadata metadata = new PaymentSessionMetadata(
                "LUMCMS501ABCD1234",
                PaymentTargetType.COMMISSION_INITIAL,
                501L,
                42L,
                expiresAt
        );

        when(safeCacheService.get("payment:session:LUMCMS501ABCD1234"))
                .thenReturn("COMMISSION_INITIAL|501|42|" + expiresAt.toEpochMilli());

        store.save(metadata, Duration.ofMinutes(30));
        Optional<PaymentSessionMetadata> loaded = store.findByPaymentCode("lumcms501abcd1234");

        verify(safeCacheService).set(
                eq("payment:session:LUMCMS501ABCD1234"),
                eq("COMMISSION_INITIAL|501|42|" + expiresAt.toEpochMilli()),
                eq(Duration.ofMinutes(30))
        );
        assertThat(loaded).isPresent();
        assertThat(loaded.get().targetType()).isEqualTo(PaymentTargetType.COMMISSION_INITIAL);
        assertThat(loaded.get().targetId()).isEqualTo(501L);
        assertThat(loaded.get().customerId()).isEqualTo(42L);
        assertThat(loaded.get().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("PAY-01 [B]: Degrades gracefully to fallback store when Redis throws CacheUnavailableException")
    void saveAndFind_redisUnavailable_fallsBackToInMemoryStore() {
        Instant expiresAt = Instant.parse("2026-11-12T17:00:00Z");
        PaymentSessionMetadata metadata = new PaymentSessionMetadata(
                "LUMREV601ABCD1234",
                PaymentTargetType.COMMISSION_REVISION,
                601L,
                88L,
                expiresAt
        );

        doThrow(new CacheUnavailableException("Redis down", null))
                .when(safeCacheService).set(any(), any(), any());
        when(safeCacheService.get("payment:session:LUMREV601ABCD1234"))
                .thenThrow(new CacheUnavailableException("Redis down", null));

        store.save(metadata, Duration.ofMinutes(15));
        Optional<PaymentSessionMetadata> loaded = store.findByPaymentCode("LUMREV601ABCD1234");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().customerId()).isEqualTo(88L);
        assertThat(loaded.get().targetType()).isEqualTo(PaymentTargetType.COMMISSION_REVISION);
    }

    @Test
    @DisplayName("PAY-01 [A]: findByPaymentCode with null or blank code returns Optional.empty")
    void findByPaymentCode_nullOrBlankCode_returnsEmpty() {
        assertThat(store.findByPaymentCode(null)).isEmpty();
        assertThat(store.findByPaymentCode("   ")).isEmpty();
    }

    @Test
    @DisplayName("PAY-01 [A]: save with null metadata or blank code is safe no-op")
    void save_nullOrBlankCode_safeNoOp() {
        store.save(null, Duration.ofMinutes(10));
        store.save(new PaymentSessionMetadata("   ", PaymentTargetType.ORDER, 1L, 1L, null), Duration.ofMinutes(10));
        assertThat(store.findByPaymentCode("   ")).isEmpty();
    }

    @Test
    @DisplayName("PAY-01 [B]: Deserialization handles malformed Redis payload by falling back to in-memory store")
    void findByPaymentCode_malformedRedisPayload_fallsBackToInMemoryStore() {
        Instant expiresAt = Instant.parse("2026-11-12T17:00:00Z");
        PaymentSessionMetadata metadata = new PaymentSessionMetadata(
                "LUMORD777ABCD1234",
                PaymentTargetType.ORDER,
                777L,
                42L,
                expiresAt
        );

        when(safeCacheService.get("payment:session:LUMORD777ABCD1234"))
                .thenReturn("CORRUPTED|DATA|NOT_ENOUGH_PARTS");

        store.save(metadata, Duration.ofMinutes(15));
        Optional<PaymentSessionMetadata> loaded = store.findByPaymentCode("LUMORD777ABCD1234");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().targetId()).isEqualTo(777L);
        assertThat(loaded.get().customerId()).isEqualTo(42L);
    }
}
