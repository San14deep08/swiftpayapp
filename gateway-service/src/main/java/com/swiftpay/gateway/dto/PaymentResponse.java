package com.swiftpay.gateway.dto;

import com.swiftpay.gateway.domain.Payment;
import com.swiftpay.gateway.domain.PaymentStatus;

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
