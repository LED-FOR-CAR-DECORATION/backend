package com.lumintra.backend.payment.infrastructure.persistence;

import com.lumintra.backend.payment.domain.service.PaymentWebhookIdempotencyStore;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class HybridPaymentWebhookIdempotencyStore implements PaymentWebhookIdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(HybridPaymentWebhookIdempotencyStore.class);
    private static final String KEY_PREFIX = "payment:webhook:sepay:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final int MAX_FALLBACK_ENTRIES = 10_000;

    private final SafeCacheService safeCacheService;
    private final Map<Long, Instant> fallbackStore = new ConcurrentHashMap<>();

    @Override
    public boolean isProcessed(Long sepayTxId) {
        if (sepayTxId == null || sepayTxId <= 0) {
            return false;
        }
        try {
            if (safeCacheService.hasKey(KEY_PREFIX + sepayTxId)) {
                return true;
            }
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable when checking SePay webhook idempotency; using fallback [sepayTxId={}]",
                    sepayTxId);
        }

        Instant expiresAt = fallbackStore.get(sepayTxId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            fallbackStore.remove(sepayTxId);
            return false;
        }
        return true;
    }

    @Override
    public void markProcessed(Long sepayTxId, String paymentCode, Duration ttl) {
        if (sepayTxId == null || sepayTxId <= 0) {
            return;
        }
        Duration effectiveTtl = (ttl != null && !ttl.isNegative() && !ttl.isZero()) ? ttl : DEFAULT_TTL;
        evictExpiredIfNeeded();
        fallbackStore.put(sepayTxId, Instant.now().plus(effectiveTtl));

        String value = (paymentCode != null && !paymentCode.isBlank()) ? paymentCode.trim() : "PROCESSED";
        try {
            safeCacheService.set(KEY_PREFIX + sepayTxId, value, effectiveTtl);
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable when recording SePay webhook idempotency; using fallback [sepayTxId={}]",
                    sepayTxId);
        }
    }

    private void evictExpiredIfNeeded() {
        if (fallbackStore.size() < MAX_FALLBACK_ENTRIES) {
            return;
        }
        Instant now = Instant.now();
        fallbackStore.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
