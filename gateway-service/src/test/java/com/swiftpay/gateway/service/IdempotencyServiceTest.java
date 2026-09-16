package com.swiftpay.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.swiftpay.gateway.domain.PaymentStatus;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.exception.DuplicateInFlightException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private IdempotencyService idempotencyService;

    private static final String TXN_ID = "txn-test-1";
    private static final String KEY = "idempotency:" + TXN_ID;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new IdempotencyService(redisTemplate, objectMapper, 24);
    }

    @Test
    void claimOrGetExisting_winsTheClaim_returnsEmpty() {
        when(valueOperations.setIfAbsent(eq(KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(true);

        Optional<PaymentResponse> result = idempotencyService.claimOrGetExisting(TXN_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void claimOrGetExisting_loserAgainstStillProcessingKey_throwsDuplicateInFlight() {
        when(valueOperations.setIfAbsent(eq(KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(KEY)).thenReturn("PROCESSING");

        assertThatThrownBy(() -> idempotencyService.claimOrGetExisting(TXN_ID))
                .isInstanceOf(DuplicateInFlightException.class);
    }

    @Test
    void claimOrGetExisting_laterDuplicateAgainstCompletedResult_replaysCachedResponse() throws Exception {
        PaymentResponse cached = new PaymentResponse(
                TXN_ID, "alice", "bob", new BigDecimal("40.00"), "USD",
                PaymentStatus.PENDING, null, "corr-1", Instant.parse("2026-01-01T00:00:00Z"));
        String cachedJson = objectMapper.writeValueAsString(cached);

        when(valueOperations.setIfAbsent(eq(KEY), eq("PROCESSING"), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(KEY)).thenReturn(cachedJson);

        Optional<PaymentResponse> result = idempotencyService.claimOrGetExisting(TXN_ID);

        assertThat(result).isPresent();
        assertThat(result.get().transactionId()).isEqualTo(TXN_ID);
        assertThat(result.get().status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void completeWithResult_storesSerializedResponseWithConfiguredTtl() {
        PaymentResponse response = new PaymentResponse(
                TXN_ID, "alice", "bob", new BigDecimal("40.00"), "USD",
                PaymentStatus.PENDING, null, "corr-1", Instant.parse("2026-01-01T00:00:00Z"));

        idempotencyService.completeWithResult(TXN_ID, response);

        org.mockito.Mockito.verify(valueOperations)
                .set(eq(KEY), anyString(), eq(Duration.ofHours(24)));
    }
}
