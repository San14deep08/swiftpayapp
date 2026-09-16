package com.swiftpay.gateway.controller;

import com.swiftpay.gateway.domain.PaymentStatus;
import com.swiftpay.gateway.dto.ErrorResponse;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Initiate a P2P payment",
            description = "Validates the request, checks the sender's balance, persists a PENDING "
                    + "payment, and emits a PaymentInitiated event for the Ledger service to process. "
                    + "transaction_id is the idempotency key: resubmitting the same transaction_id "
                    + "within 24h returns the original result instead of processing it again.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Payment accepted and queued for the Ledger service (status=PENDING)"),
            @ApiResponse(responseCode = "422", description = "Sender has insufficient funds (status=FAILED in body; no ledger processing occurs)"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Unknown sender_id or receiver_id", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Same transaction_id is currently being processed by another request", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(request);
        HttpStatus status = response.status() == PaymentStatus.FAILED
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }
}
