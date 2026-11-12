package com.lumintra.backend.payment.application;

import com.lumintra.backend.commerce.domain.model.OrderPaymentSnapshot;
import com.lumintra.backend.commerce.domain.service.OrderPaymentLookupService;
import com.lumintra.backend.payment.api.request.SePayWebhookRequest;
import com.lumintra.backend.payment.api.response.SePayWebhookResponse;
import com.lumintra.backend.payment.domain.event.PaymentSucceededEvent;
import com.lumintra.backend.payment.domain.model.PaymentSessionMetadata;
import com.lumintra.backend.payment.domain.model.PaymentStatus;
import com.lumintra.backend.payment.domain.model.PaymentTargetType;
import com.lumintra.backend.payment.domain.service.PaymentSessionMetadataStore;
import com.lumintra.backend.payment.domain.service.PaymentWebhookIdempotencyStore;
import com.lumintra.backend.payment.infrastructure.integration.SePayProperties;
import com.lumintra.backend.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(SePayProperties.class)
public class ProcessSePayWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessSePayWebhookUseCase.class);

    static final String OUTCOME_PROCESSED = "PROCESSED";
    static final String OUTCOME_IGNORED = "IGNORED";
    static final String OUTCOME_UNMATCHED = "UNMATCHED";

    private static final String APIKEY_HEADER = "Apikey";
    private static final String APIKEY_SCHEME_PREFIX = "Apikey ";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentSessionMetadataStore metadataStore;
    private final PaymentWebhookIdempotencyStore idempotencyStore;
    private final OrderPaymentLookupService orderPaymentLookupService;
    private final SePayProperties sePayProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SePayWebhookResponse execute(HttpHeaders headers, SePayWebhookRequest request) {
        verifyApikeyHeader(headers);

        Long sepayTxId = request.id();
        if (sepayTxId <= 0) {
            log.info("SePay synthetic test webhook acknowledged [sepayTxId={}]", sepayTxId);
            return buildOutcomeResponse(
                    sepayTxId,
                    null,
                    request.normalizedCode(),
                    null,
                    null,
                    null,
                    OUTCOME_IGNORED,
                    "Synthetic SePay test webhook acknowledged without modifying payment state."
            );
        }

        if (idempotencyStore.isProcessed(sepayTxId)) {
            log.info("SePay webhook ignored: duplicate sepay_tx_id [sepayTxId={}]", sepayTxId);
            return buildOutcomeResponse(
                    sepayTxId,
                    null,
                    request.normalizedCode(),
                    null,
                    null,
                    null,
                    OUTCOME_IGNORED,
                    "Duplicate webhook ignored because sepay_tx_id was already processed."
            );
        }

        if (!request.isIncomingTransfer()) {
            log.info("SePay webhook ignored: non-incoming transfer type [sepayTxId={}] [transferType={}]",
                    sepayTxId, request.transferType());
            return buildOutcomeResponse(
                    sepayTxId,
                    null,
                    request.normalizedCode(),
                    null,
                    null,
                    null,
                    OUTCOME_IGNORED,
                    "Ignored non-incoming bank transfer webhook."
            );
        }

        Optional<MatchedTransaction> matchedOpt = findMatchingTransaction(request);
        if (matchedOpt.isEmpty()) {
            log.warn("SePay webhook unmatched: no payment transaction matched [sepayTxId={}] [code={}]",
                    sepayTxId, request.normalizedCode());
            return buildOutcomeResponse(
                    sepayTxId,
                    null,
                    request.normalizedCode(),
                    null,
                    null,
                    null,
                    OUTCOME_UNMATCHED,
                    "No matching payment transaction found for webhook transfer."
            );
        }

        MatchedTransaction matched = matchedOpt.get();
        PaymentTransactionEntity transaction = matched.entity();
        String paymentCode = transaction.getTransactionCode() != null && !transaction.getTransactionCode().isBlank()
                ? transaction.getTransactionCode()
                : matched.matchedCode();

        Optional<PaymentSessionMetadata> metadataOpt = metadataStore.findByPaymentCode(paymentCode);
        PaymentTargetType targetType = metadataOpt
                .map(PaymentSessionMetadata::targetType)
                .orElseGet(() -> PaymentTargetType.fromPaymentCode(paymentCode));

        PaymentStatus currentStatus = transaction.getPaymentStatus();
        if (currentStatus == PaymentStatus.SUCCEEDED) {
            idempotencyStore.markProcessed(sepayTxId, paymentCode, IDEMPOTENCY_TTL);
            log.info("SePay webhook ignored: payment transaction already succeeded [sepayTxId={}] [paymentId={}] [paymentCode={}]",
                    sepayTxId, transaction.getPaymentId(), paymentCode);
            return buildOutcomeResponse(
                    sepayTxId,
                    transaction.getPaymentId(),
                    paymentCode,
                    targetType,
                    transaction.getOrderId(),
                    PaymentStatus.SUCCEEDED,
                    OUTCOME_IGNORED,
                    "Payment transaction is already succeeded."
            );
        }

        if (currentStatus != PaymentStatus.PENDING) {
            log.info("SePay webhook ignored: payment transaction in non-pending status [sepayTxId={}] [paymentId={}] [status={}]",
                    sepayTxId, transaction.getPaymentId(), currentStatus);
            return buildOutcomeResponse(
                    sepayTxId,
                    transaction.getPaymentId(),
                    paymentCode,
                    targetType,
                    transaction.getOrderId(),
                    currentStatus,
                    OUTCOME_IGNORED,
                    "Webhook ignored because payment transaction is in terminal status " + currentStatus + "."
            );
        }

        if (request.transferAmount().compareTo(transaction.getAmount()) < 0) {
            log.warn("SePay webhook ignored: transfer amount is insufficient [sepayTxId={}] [paymentId={}] [expectedAmount={}] [receivedAmount={}]",
                    sepayTxId, transaction.getPaymentId(), transaction.getAmount(), request.transferAmount());
            return buildOutcomeResponse(
                    sepayTxId,
                    transaction.getPaymentId(),
                    paymentCode,
                    targetType,
                    transaction.getOrderId(),
                    PaymentStatus.PENDING,
                    OUTCOME_IGNORED,
                    "Webhook ignored because transferAmount is less than expected payment amount."
            );
        }

        Instant paidAt = request.resolvedPaidAt(Instant.now());
        int updated = paymentTransactionRepository.markAsSucceededIfPending(
                transaction.getPaymentId(),
                PaymentTransactionEntity.DB_STATUS_SUCCESS,
                paidAt
        );
        if (updated == 0) {
            idempotencyStore.markProcessed(sepayTxId, paymentCode, IDEMPOTENCY_TTL);
            log.info("SePay webhook ignored: concurrent transaction update detected [sepayTxId={}] [paymentId={}]",
                    sepayTxId, transaction.getPaymentId());
            return buildOutcomeResponse(
                    sepayTxId,
                    transaction.getPaymentId(),
                    paymentCode,
                    targetType,
                    transaction.getOrderId(),
                    PaymentStatus.SUCCEEDED,
                    OUTCOME_IGNORED,
                    "Payment transaction was already finalized concurrently."
            );
        }

        Long customerId = resolveCustomerId(metadataOpt, targetType, transaction.getOrderId());
        idempotencyStore.markProcessed(sepayTxId, paymentCode, IDEMPOTENCY_TTL);

        eventPublisher.publishEvent(new PaymentSucceededEvent(
                transaction.getPaymentId(),
                paymentCode,
                targetType,
                transaction.getOrderId(),
                customerId,
                transaction.getAmount(),
                sepayTxId,
                request.normalizedReferenceCode(),
                paidAt
        ));

        log.info("SePay webhook processed [sepayTxId={}] [paymentId={}] [paymentCode={}] [targetType={}] [targetId={}]",
                sepayTxId, transaction.getPaymentId(), paymentCode, targetType, transaction.getOrderId());

        return buildOutcomeResponse(
                sepayTxId,
                transaction.getPaymentId(),
                paymentCode,
                targetType,
                transaction.getOrderId(),
                PaymentStatus.SUCCEEDED,
                OUTCOME_PROCESSED,
                "Payment confirmed and PaymentSucceededEvent published."
        );
    }

    private void verifyApikeyHeader(HttpHeaders headers) {
        String expectedApiKey = sePayProperties.resolvedWebhookApiKey();
        if (expectedApiKey == null) {
            log.error("SePay webhook verification failed: SEPAY_WEBHOOK_API_KEY is not configured");
            throw new BadCredentialsException("Invalid SePay webhook credentials.");
        }

        String authorizationHeader = headers != null ? headers.getFirst(HttpHeaders.AUTHORIZATION) : null;
        String apikeyHeader = headers != null ? headers.getFirst(APIKEY_HEADER) : null;

        boolean validAuthorization = matchesApikeyCredential(authorizationHeader, true, expectedApiKey);
        boolean validDirectApikey = matchesApikeyCredential(apikeyHeader, false, expectedApiKey);

        if (!validAuthorization && !validDirectApikey) {
            log.warn("SePay webhook verification failed: invalid or missing Apikey header [authorizationPresent={}] [apikeyHeaderPresent={}]",
                    authorizationHeader != null && !authorizationHeader.isBlank(),
                    apikeyHeader != null && !apikeyHeader.isBlank());
            throw new BadCredentialsException("Invalid SePay webhook credentials.");
        }
    }

    private boolean matchesApikeyCredential(String rawValue, boolean requireScheme, String expectedApiKey) {
        if (rawValue == null || rawValue.isBlank()) {
            return false;
        }
        String trimmed = rawValue.trim();
        String candidate;
        if (trimmed.length() > APIKEY_SCHEME_PREFIX.length()
                && trimmed.regionMatches(true, 0, APIKEY_SCHEME_PREFIX, 0, APIKEY_SCHEME_PREFIX.length())) {
            candidate = trimmed.substring(APIKEY_SCHEME_PREFIX.length()).trim();
        } else if (requireScheme) {
            return false;
        } else {
            candidate = trimmed;
        }
        return constantTimeEquals(candidate, expectedApiKey);
    }

    private boolean constantTimeEquals(String candidate, String expected) {
        if (candidate == null || candidate.isBlank() || expected == null || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Optional<MatchedTransaction> findMatchingTransaction(SePayWebhookRequest request) {
        Set<String> candidateCodes = extractCandidateCodes(request);
        for (String candidate : candidateCodes) {
            Optional<PaymentTransactionEntity> exact = paymentTransactionRepository
                    .findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(candidate);
            if (exact.isPresent()) {
                return Optional.of(new MatchedTransaction(candidate, exact.get()));
            }
            String upper = candidate.toUpperCase(Locale.ROOT);
            if (!upper.equals(candidate)) {
                Optional<PaymentTransactionEntity> byUpper = paymentTransactionRepository
                        .findFirstByTransactionCodeOrderByCreatedAtDescPaymentIdDesc(upper);
                if (byUpper.isPresent()) {
                    return Optional.of(new MatchedTransaction(upper, byUpper.get()));
                }
            }
        }

        for (Long orderId : extractOrderIdFallbacks(request, candidateCodes)) {
            Optional<PaymentTransactionEntity> byOrderId = paymentTransactionRepository
                    .findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId);
            if (byOrderId.isPresent()) {
                return Optional.of(new MatchedTransaction(
                        sePayProperties.normalizedPaymentPrefix() + orderId,
                        byOrderId.get()
                ));
            }
        }
        return Optional.empty();
    }

    private Set<String> extractCandidateCodes(SePayWebhookRequest request) {
        Set<String> candidates = new LinkedHashSet<>();
        if (request.normalizedCode() != null) {
            candidates.add(request.normalizedCode());
        }

        String quotedPrefix = Pattern.quote(sePayProperties.normalizedPaymentPrefix());
        Pattern structuredCodePattern = Pattern.compile(
                "\\b(" + quotedPrefix + "(?:ORD|CMS|CMR|REV)\\d+[A-Z0-9]+)\\b",
                Pattern.CASE_INSENSITIVE
        );
        Pattern legacySeedPattern = Pattern.compile(
                "\\b(" + quotedPrefix + "-SEED-TXN-\\d+)\\b",
                Pattern.CASE_INSENSITIVE
        );

        for (String text : narrativeTexts(request)) {
            addRegexMatches(candidates, structuredCodePattern, text);
            addRegexMatches(candidates, legacySeedPattern, text);
        }
        return candidates;
    }

    private List<Long> extractOrderIdFallbacks(SePayWebhookRequest request, Set<String> candidates) {
        List<Long> orderIds = new ArrayList<>();
        String quotedPrefix = Pattern.quote(sePayProperties.normalizedPaymentPrefix());
        Pattern legacyOrderPattern = Pattern.compile("\\b" + quotedPrefix + "(\\d+)\\b", Pattern.CASE_INSENSITIVE);

        for (String candidate : candidates) {
            addOrderIdMatches(orderIds, legacyOrderPattern, candidate);
        }
        for (String text : narrativeTexts(request)) {
            addOrderIdMatches(orderIds, legacyOrderPattern, text);
        }
        return orderIds;
    }

    private List<String> narrativeTexts(SePayWebhookRequest request) {
        List<String> texts = new ArrayList<>(3);
        if (request.normalizedCode() != null) {
            texts.add(request.normalizedCode());
        }
        if (request.normalizedContent() != null) {
            texts.add(request.normalizedContent());
        }
        if (request.normalizedDescription() != null) {
            texts.add(request.normalizedDescription());
        }
        return texts;
    }

    private void addRegexMatches(Set<String> target, Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            target.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
    }

    private void addOrderIdMatches(List<Long> target, Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            try {
                long orderId = Long.parseLong(matcher.group(1));
                if (orderId > 0 && !target.contains(orderId)) {
                    target.add(orderId);
                }
            } catch (NumberFormatException ignored) {
                // Ignore oversized numeric sequences in arbitrary transfer text
            }
        }
    }

    private Long resolveCustomerId(
            Optional<PaymentSessionMetadata> metadataOpt,
            PaymentTargetType targetType,
            Long targetId) {
        if (metadataOpt.isPresent() && metadataOpt.get().customerId() != null) {
            return metadataOpt.get().customerId();
        }
        if (targetType == PaymentTargetType.ORDER && targetId != null) {
            return orderPaymentLookupService.findByOrderId(targetId)
                    .map(OrderPaymentSnapshot::customerId)
                    .orElse(null);
        }
        return null;
    }

    private SePayWebhookResponse buildOutcomeResponse(
            Long sepayTxId,
            Long paymentId,
            String paymentCode,
            PaymentTargetType targetType,
            Long targetId,
            PaymentStatus status,
            String outcome,
            String message) {
        return SePayWebhookResponse.builder()
                .sepayTxId(sepayTxId)
                .paymentId(paymentId)
                .paymentCode(paymentCode)
                .targetType(targetType)
                .targetId(targetId)
                .status(status)
                .outcome(outcome)
                .message(message)
                .build();
    }

    private record MatchedTransaction(
            String matchedCode,
            PaymentTransactionEntity entity
    ) {}
}
