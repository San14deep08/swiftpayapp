package com.swiftpay.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentInitiatedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentInitiatedListener.class);
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final ObjectMapper objectMapper;
    private final LedgerTransactionService ledgerTransactionService;

    public PaymentInitiatedListener(ObjectMapper objectMapper,
                                     LedgerTransactionService ledgerTransactionService) {
        this.objectMapper = objectMapper;
        this.ledgerTransactionService = ledgerTransactionService;
    }

    @KafkaListener(topics = "${swiftpay.kafka.topic.payment-initiated}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) throws Exception {
        // A malformed payload can never succeed on retry — let it propagate
        // as-is (JsonProcessingException is registered non-retryable in
        // KafkaConsumerConfig) so it goes straight to the DLQ.
        PaymentInitiatedEvent event = objectMapper.readValue(payload, PaymentInitiatedEvent.class);

        MDC.put(CORRELATION_ID_MDC_KEY, event.correlationId());
        try {
            log.info("Consumed PaymentInitiated txn={}", event.transactionId());
            ledgerTransactionService.processPaymentInitiated(event);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
