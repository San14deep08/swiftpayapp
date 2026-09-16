package com.swiftpay.gateway.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Serialized as JSON onto the "payment.initiated" topic.
 * NOTE: duplicated (not shared via a common module) between gateway-service
 * and ledger-service for now — TODO: extract to a shared events module if
 * the event contract needs to grow beyond this hackathon scope.
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
