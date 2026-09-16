package com.swiftpay.gateway.service;

import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Entry point for the payment-initiation use case. Deliberately thin: the
 * idempotency claim (Redis, outside any DB transaction) happens here, and
 * the actual persistence + event publication is delegated to
 * PaymentPersistenceService so it runs inside a real Spring-managed
 * transaction (see that class for why this can't just be a private method
 * on this same bean).
 *
 * NOTE on the crash-between-claim-and-persist gap: the idempotency claim
 * lives in Redis, outside the DB transaction below. If the process crashes
 * after claiming the Redis key but before persisting, the key sits as
 * "PROCESSING" until its 24h TTL expires. Accepted hackathon-scope gap --
 * TODO: a shorter "in-flight" TTL distinct from the 24h dedupe TTL would
 * let retries through sooner after a crash.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final IdempotencyService idempotencyService;
    private final PaymentPersistenceService paymentPersistenceService;

    public PaymentService(IdempotencyService idempotencyService,
                           PaymentPersistenceService paymentPersistenceService) {
        this.idempotencyService = idempotencyService;
        this.paymentPersistenceService = paymentPersistenceService;
    }

    public PaymentResponse initiatePayment(PaymentRequest request) {
        Optional<PaymentResponse> cached = idempotencyService.claimOrGetExisting(request.transactionId());
        if (cached.isPresent()) {
            log.info("Idempotent replay for txn={}", request.transactionId());
            return cached.get();
        }

        PaymentResponse response = paymentPersistenceService.processNewPayment(request);
        idempotencyService.completeWithResult(request.transactionId(), response);
        return response;
    }
}
