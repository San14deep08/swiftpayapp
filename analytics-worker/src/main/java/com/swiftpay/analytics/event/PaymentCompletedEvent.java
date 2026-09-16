package com.swiftpay.analytics.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Deserialized from the "payment.completed" topic. NOTE: duplicated (not
 * shared) with ledger-service's producer-side record of the same shape —
 * see that class's TODO about a shared events module.
 */
public record PaymentCompletedEvent(
        String transactionId,
        String senderId,
        String receiverId,
        BigDecimal amount,
        String currency,
        String correlationId,
        Instant completedAt
) {
}
