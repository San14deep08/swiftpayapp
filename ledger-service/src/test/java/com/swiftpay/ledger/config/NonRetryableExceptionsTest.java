package com.swiftpay.ledger.config;

import com.fasterxml.jackson.core.JsonParseException;
import com.swiftpay.ledger.exception.PaymentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the classification KafkaConsumerConfig relies on, without
 * needing a real Kafka broker or a real Postgres outage to exercise it.
 *
 * The DataAccessResourceFailureException / QueryTimeoutException cases are
 * the ones that actually matter for the consumer-resilience correctness
 * rule: these are what Spring's JDBC/Hikari layer throws when Postgres is
 * unreachable, and this test is the guarantee that they are NOT
 * accidentally classified as non-retryable (which would send every
 * DB-outage failure straight to the DLQ instead of retrying).
 */
class NonRetryableExceptionsTest {

    @Test
    void paymentNotFound_isNonRetryable() {
        assertThat(NonRetryableExceptions.isNonRetryable(
                new PaymentNotFoundException("no such transaction_id"))).isTrue();
    }

    @Test
    void malformedJson_isNonRetryable() throws Exception {
        // JsonParseException is a subtype of JsonProcessingException — the
        // actual exception ObjectMapper throws on genuinely malformed JSON.
        assertThat(NonRetryableExceptions.isNonRetryable(
                new JsonParseException(null, "unexpected token"))).isTrue();
    }

    @Test
    void illegalArgument_isNonRetryable() {
        assertThat(NonRetryableExceptions.isNonRetryable(
                new IllegalArgumentException("bad input"))).isTrue();
    }

    @Test
    void dbConnectivityFailure_isRetryable_notSentStraightToDlq() {
        // This is the exception family a real Postgres outage actually
        // produces through Spring's DataAccessException hierarchy.
        assertThat(NonRetryableExceptions.isNonRetryable(
                new DataAccessResourceFailureException("connection refused"))).isFalse();
    }

    @Test
    void queryTimeout_isRetryable() {
        assertThat(NonRetryableExceptions.isNonRetryable(
                new QueryTimeoutException("statement timeout"))).isFalse();
    }

    @Test
    void genericUnexpectedException_defaultsToRetryable() {
        // Fail-safe default: an exception type nobody has specifically
        // classified should retry rather than silently skip straight to
        // the DLQ, since retrying is the safer failure mode for an unknown
        // cause.
        assertThat(NonRetryableExceptions.isNonRetryable(
                new RuntimeException("something unexpected"))).isFalse();
    }
}
