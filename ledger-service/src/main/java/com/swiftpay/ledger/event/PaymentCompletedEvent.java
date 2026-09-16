package com.swiftpay.ledger.event;

import java.math.BigDecimal;
import java.time.Instant;

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
