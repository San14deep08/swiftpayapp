package com.swiftpay.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.gateway.event.PaymentInitiatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String paymentInitiatedTopic;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           @Value("${swiftpay.kafka.topic.payment-initiated}") String paymentInitiatedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.paymentInitiatedTopic = paymentInitiatedTopic;
    }

    /**
     * Publishes with the transactionId as the Kafka message key so that all
     * events for a given payment land on the same partition and are
     * processed in order by a single consumer.
     */
    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(paymentInitiatedTopic, event.transactionId(), payload);
            log.info("Published PaymentInitiated txn={} correlationId={}",
                    event.transactionId(), event.correlationId());
        } catch (JsonProcessingException e) {
            // Serialization of our own DTO failing indicates a programming error,
            // not a transient fault — fail loudly rather than silently dropping the event.
            throw new IllegalStateException("Failed to serialize PaymentInitiatedEvent", e);
        }
    }
}
