package com.swiftpay.ledger.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Spring Boot's Kafka autoconfiguration picks up a CommonErrorHandler bean
 * automatically and wires it into the auto-configured listener container
 * factory — no need to redefine the whole factory ourselves.
 *
 * The actual retryable/non-retryable classification lives in
 * NonRetryableExceptions, unit-tested directly in
 * NonRetryableExceptionsTest — that test is the real guarantee that, e.g.,
 * a DB connectivity failure (org.springframework.dao.DataAccessResourceFailureException)
 * is NOT accidentally treated as non-retryable, and that PaymentNotFoundException
 * IS.
 *
 * What's still unverified: whether Spring Kafka's DefaultErrorHandler sees
 * through the ListenerExecutionFailedException wrapper to classify the
 * actual cause correctly at runtime, and the exponential-backoff timing
 * itself, since exercising that needs a real broker plus a real DB outage —
 * see the README's manual verification log for the two attempts at that and
 * why they didn't conclusively prove it.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaOperations<String, String> kafkaOperations,
            @Value("${swiftpay.kafka.topic.payment-initiated-dlq}") String dlqTopic) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> new TopicPartition(dlqTopic, record.partition()));

        // 1s, 2s, 4s, 8s... capped at 30s between attempts, giving up after
        // ~2 minutes total so a genuinely down DB doesn't retry forever
        // before the message is parked in the DLQ for manual replay.
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(120_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(NonRetryableExceptions.TYPES);

        return errorHandler;
    }
}
