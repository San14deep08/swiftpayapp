package com.swiftpay.gateway.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard error envelope for all non-2xx responses across SwiftPay services.
 * Keep this shape identical in ledger-service so clients handle errors uniformly.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, path, details);
    }
}
