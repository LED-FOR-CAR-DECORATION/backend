package com.lumintra.backend.payment.infrastructure.persistence;

import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.shared.caching.CacheUnavailableException;
import com.lumintra.backend.shared.caching.SafeCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class HybridPaymentSessionMetadataStore implements PaymentSessionMetadataStore {

    private static final Logger log = LoggerFactory.getLogger(HybridPaymentSessionMetadataStore.class);
    private static final String KEY_PREFIX = "payment:session:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final SafeCacheService safeCacheService;
    private final Map<String, PaymentSessionMetadata> fallbackStore = new ConcurrentHashMap<>();

    @Override
    public void save(PaymentSessionMetadata metadata, Duration ttl) {
        if (metadata == null || metadata.paymentCode() == null || metadata.paymentCode().isBlank()) {
            return;
        }
        String normalizedCode = normalizeCode(metadata.paymentCode());
        Duration effectiveTtl = (ttl != null && !ttl.isNegative() && !ttl.isZero()) ? ttl : DEFAULT_TTL;
        fallbackStore.put(normalizedCode, metadata);

        try {
            safeCacheService.set(KEY_PREFIX + normalizedCode, serialize(metadata), effectiveTtl);
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable when caching payment session metadata; using fallback [paymentCode={}]",
                    normalizedCode);
        }
    }

    @Override
    public Optional<PaymentSessionMetadata> findByPaymentCode(String paymentCode) {
        if (paymentCode == null || paymentCode.isBlank()) {
            return Optional.empty();
        }
        String normalizedCode = normalizeCode(paymentCode);

        try {
            String raw = safeCacheService.get(KEY_PREFIX + normalizedCode);
            if (raw != null && !raw.isBlank()) {
                Optional<PaymentSessionMetadata> parsed = deserialize(normalizedCode, raw);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        } catch (CacheUnavailableException ex) {
            log.warn("Redis unavailable when reading payment session metadata; using fallback [paymentCode={}]",
                    normalizedCode);
        }

        return Optional.ofNullable(fallbackStore.get(normalizedCode));
    }

    private String normalizeCode(String paymentCode) {
        return paymentCode.trim().toUpperCase(Locale.ROOT);
    }

    private String serialize(PaymentSessionMetadata metadata) {
        String targetType = metadata.targetType() != null ? metadata.targetType().name() : PaymentTargetType.ORDER.name();
        long targetId = metadata.targetId() != null ? metadata.targetId() : 0L;
        long customerId = metadata.customerId() != null ? metadata.customerId() : 0L;
        long expiresAtMillis = metadata.expiresAt() != null ? metadata.expiresAt().toEpochMilli() : -1L;
        return targetType + "|" + targetId + "|" + customerId + "|" + expiresAtMillis;
    }

    private Optional<PaymentSessionMetadata> deserialize(String paymentCode, String raw) {
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            PaymentTargetType targetType = PaymentTargetType.valueOf(parts[0]);
            Long targetId = Long.parseLong(parts[1]);
            Long customerId = Long.parseLong(parts[2]);
            long expiresAtMillis = Long.parseLong(parts[3]);
            Instant expiresAt = expiresAtMillis >= 0 ? Instant.ofEpochMilli(expiresAtMillis) : null;
            return Optional.of(new PaymentSessionMetadata(paymentCode, targetType, targetId, customerId, expiresAt));
        } catch (RuntimeException ex) {
            log.warn("Malformed cached payment session metadata [paymentCode={}]", paymentCode);
            return Optional.empty();
        }
    }
}
