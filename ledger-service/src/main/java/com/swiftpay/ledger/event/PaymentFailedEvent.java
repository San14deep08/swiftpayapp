package com.swiftpay.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentFailedEvent(
        String transactionId,
        String senderId,
        String receiverId,
        BigDecimal amount,
        String currency,
        String correlationId,
        String reason,
        Instant failedAt
) {
}
