package com.swiftpay.ledger.exception;

/**
 * Thrown when a PaymentInitiated event references a transaction_id or
 * account that doesn't exist in Postgres. This can never succeed on retry
 * (the row isn't going to appear later), so it's registered as
 * non-retryable in KafkaConsumerConfig and routed straight to the DLQ
 * instead of being retried with backoff.
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
