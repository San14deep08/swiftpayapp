package com.swiftpay.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.exception.DuplicateInFlightException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Guarantees exactly-one-winner semantics for a given transaction_id within
 * the configured TTL window, using Redis SET NX as the atomic claim.
 *
 * States stored under key "idempotency:{transactionId}":
 *  - "PROCESSING"        -> a request currently owns this key; racers must back off
 *  - "<json response>"   -> the final result of a completed attempt, replayed verbatim
 */
@Service
public class IdempotencyService {

    private static final String PROCESSING_MARKER = "PROCESSING";
    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public IdempotencyService(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               @Value("${swiftpay.idempotency.ttl-hours}") long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Attempts to claim the idempotency key for this transaction.
     *
     * @return the cached response if this transaction already completed
     *         (empty if this call is the one that just claimed the key and
     *         should proceed to do the real work)
     * @throws DuplicateInFlightException if another request currently owns
     *         the key and has not finished yet
     */
    public Optional<PaymentResponse> claimOrGetExisting(String transactionId) {
        String key = KEY_PREFIX + transactionId;
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(key, PROCESSING_MARKER, ttl);

        if (Boolean.TRUE.equals(claimed)) {
            return Optional.empty(); // we own it now — caller proceeds
        }

        String existing = redisTemplate.opsForValue().get(key);
        if (existing == null || PROCESSING_MARKER.equals(existing)) {
            throw new DuplicateInFlightException(
                    "transaction_id " + transactionId + " is already being processed, retry shortly");
        }

        try {
            return Optional.of(objectMapper.readValue(existing, PaymentResponse.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt idempotency cache entry for " + transactionId, e);
        }
    }

    /**
     * Stores the final response so later (non-racing) duplicate submissions
     * within the TTL window get the same result replayed instead of
     * re-processing.
     */
    public void completeWithResult(String transactionId, PaymentResponse response) {
        String key = KEY_PREFIX + transactionId;
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, payload, ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response for idempotency cache", e);
        }
    }
}
