package com.swiftpay.ledger.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.event.PaymentFailedEvent;
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
    private final String completedTopic;
    private final String failedTopic;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           @Value("${swiftpay.kafka.topic.payment-completed}") String completedTopic,
                           @Value("${swiftpay.kafka.topic.payment-failed}") String failedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.completedTopic = completedTopic;
        this.failedTopic = failedTopic;
    }

    public void publishCompleted(PaymentCompletedEvent event) {
        send(completedTopic, event.transactionId(), event, "PaymentCompleted");
    }

    public void publishFailed(PaymentFailedEvent event) {
        send(failedTopic, event.transactionId(), event, "PaymentFailed");
    }

    private void send(String topic, String key, Object event, String eventName) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, payload);
            log.info("Published {} txn={}", eventName, key);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + eventName + " event", e);
        }
    }
}
