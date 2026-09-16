package com.swiftpay.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Deserialized from the "payment.initiated" topic.
 * NOTE: duplicated (not shared) with gateway-service's producer-side record
 * of the same shape — see that class's TODO about a shared events module.
 */
public record PaymentInitiatedEvent(
        String transactionId,
        String senderId,
        String receiverId,
        BigDecimal amount,
        String currency,
        String correlationId,
        Instant occurredAt
) {
}
