package com.swiftpay.analytics.service;

import com.swiftpay.analytics.domain.PaymentEvent;
import com.swiftpay.analytics.event.PaymentCompletedEvent;
import com.swiftpay.analytics.repository.PaymentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from PaymentCompletedListener specifically so @Transactional is
 * applied through Spring's proxy (a call from another bean), not via
 * self-invocation within the same class — see PaymentPersistenceService in
 * gateway-service for the fuller explanation of why that matters.
 */
@Service
public class PaymentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIngestionService.class);

    private final PaymentEventRepository paymentEventRepository;

    public PaymentIngestionService(PaymentEventRepository paymentEventRepository) {
        this.paymentEventRepository = paymentEventRepository;
    }

    @Transactional
    public void ingest(PaymentCompletedEvent event) {
        // Guards against Kafka at-least-once redelivery creating a
        // duplicate analytics row; the transaction_id unique constraint at
        // the DB level is the backstop if this check is ever bypassed.
        if (paymentEventRepository.existsByTransactionId(event.transactionId())) {
            log.info("txn={} already ingested — skipping duplicate (Kafka redelivery)", event.transactionId());
            return;
        }

        paymentEventRepository.save(new PaymentEvent(
                event.transactionId(), event.senderId(), event.receiverId(),
                event.amount(), event.currency(), event.completedAt()));

        log.info("Ingested txn={} amount={} {}", event.transactionId(), event.amount(), event.currency());
    }
}
