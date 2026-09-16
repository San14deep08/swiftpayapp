package com.swiftpay.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.analytics.event.PaymentCompletedEvent;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentCompletedListener {

    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final ObjectMapper objectMapper;
    private final PaymentIngestionService paymentIngestionService;

    public PaymentCompletedListener(ObjectMapper objectMapper,
                                     PaymentIngestionService paymentIngestionService) {
        this.objectMapper = objectMapper;
        this.paymentIngestionService = paymentIngestionService;
    }

    @KafkaListener(topics = "${swiftpay.kafka.topic.payment-completed}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) throws Exception {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);

        MDC.put(CORRELATION_ID_MDC_KEY, event.correlationId());
        try {
            paymentIngestionService.ingest(event);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
