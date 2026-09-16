package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.dto.PaymentResponse;
import com.swiftpay.ledger.repository.PaymentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class PaymentHistoryController {

    private final PaymentRepository paymentRepository;

    public PaymentHistoryController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/users/{userId}/transactions")
    @Operation(summary = "Transaction history for a user",
            description = "Returns payments where the given user was either the sender or the "
                    + "receiver, most recent first.")
    public Page<PaymentResponse> getTransactionHistory(
            @PathVariable String userId,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return paymentRepository
                .findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId, pageable)
                .map(PaymentResponse::from);
    }
}
