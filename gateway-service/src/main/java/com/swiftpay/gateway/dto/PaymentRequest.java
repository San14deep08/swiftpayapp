package com.swiftpay.gateway.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Incoming POST /v1/payments body.
 * transaction_id is client-supplied and is the idempotency key.
 */
public record PaymentRequest(

        @NotBlank(message = "transaction_id is required")
        String transactionId,

        @NotBlank(message = "sender_id is required")
        String senderId,

        @NotBlank(message = "receiver_id is required")
        String receiverId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO code, e.g. USD")
        String currency
) {
}
