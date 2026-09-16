package com.swiftpay.ledger.dto;

import com.swiftpay.ledger.domain.Payment;
import com.swiftpay.ledger.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String transactionId,
        String senderId,
        String receiverId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureReason,
        String correlationId,
        Instant createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getTransactionId(),
                payment.getSenderId(),
                payment.getReceiverId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getCorrelationId(),
                payment.getCreatedAt()
        );
    }
}
