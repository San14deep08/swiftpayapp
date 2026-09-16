package com.swiftpay.gateway.exception;

/**
 * Thrown when a request's idempotency key is currently being processed by
 * another (racing) request that reached Redis first. The loser must not
 * proceed — it is told to retry rather than being allowed to double-process.
 */
public class DuplicateInFlightException extends RuntimeException {
    public DuplicateInFlightException(String message) {
        super(message);
    }
}
