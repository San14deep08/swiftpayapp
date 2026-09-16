package com.swiftpay.ledger.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.swiftpay.ledger.exception.PaymentNotFoundException;

/**
 * Single source of truth for which exceptions from PaymentInitiatedListener
 * can never succeed on retry and should go straight to the DLQ, versus
 * everything else (in particular DB connectivity failures like
 * org.springframework.dao.DataAccessResourceFailureException) which is
 * assumed transient and gets the exponential-backoff retry treatment.
 *
 * Pulled out of KafkaConsumerConfig specifically so this classification can
 * be unit-tested directly (see NonRetryableExceptionsTest) without needing
 * a real Kafka broker or reflecting into Spring Kafka's internal
 * BinaryExceptionClassifier.
 */
public final class NonRetryableExceptions {

    @SuppressWarnings("unchecked")
    public static final Class<? extends Exception>[] TYPES = new Class[] {
            PaymentNotFoundException.class,
            JsonProcessingException.class,
            IllegalArgumentException.class
    };

    private NonRetryableExceptions() {
    }

    public static boolean isNonRetryable(Throwable t) {
        for (Class<? extends Exception> type : TYPES) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
