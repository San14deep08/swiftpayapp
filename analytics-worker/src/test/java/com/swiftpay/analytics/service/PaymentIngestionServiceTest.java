package com.swiftpay.analytics.service;

import com.swiftpay.analytics.dto.CurrencyVolume;
import com.swiftpay.analytics.event.PaymentCompletedEvent;
import com.swiftpay.analytics.repository.PaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real Postgres via Testcontainers, calling
 * PaymentIngestionService directly rather than through Kafka — same
 * rationale as LedgerTransactionServiceTest in ledger-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class PaymentIngestionServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withInitScript("test-schema.sql");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentIngestionService paymentIngestionService;
    @Autowired
    private PaymentEventRepository paymentEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE analytics.payment_events");
    }

    private PaymentCompletedEvent eventFor(String transactionId, String amount, String currency) {
        return new PaymentCompletedEvent(transactionId, "alice", "bob",
                new BigDecimal(amount), currency, "corr-" + transactionId, Instant.now());
    }

    @Test
    void ingest_createsOneRow() {
        paymentIngestionService.ingest(eventFor("txn-1", "40.00", "USD"));

        assertThat(paymentEventRepository.existsByTransactionId("txn-1")).isTrue();
        assertThat(paymentEventRepository.count()).isEqualTo(1);
    }

    @Test
    void redeliveredEvent_doesNotCreateDuplicateRow() {
        PaymentCompletedEvent event = eventFor("txn-2", "25.00", "USD");

        paymentIngestionService.ingest(event);
        paymentIngestionService.ingest(event); // simulates Kafka at-least-once redelivery

        assertThat(paymentEventRepository.count()).isEqualTo(1);
    }

    @Test
    void volumeByCurrency_aggregatesCorrectly() {
        paymentIngestionService.ingest(eventFor("txn-3", "10.00", "USD"));
        paymentIngestionService.ingest(eventFor("txn-4", "15.00", "USD"));
        paymentIngestionService.ingest(eventFor("txn-5", "100.00", "EUR"));

        List<CurrencyVolume> volumes = paymentEventRepository.volumeByCurrency();

        CurrencyVolume usd = volumes.stream().filter(v -> v.currency().equals("USD")).findFirst().orElseThrow();
        CurrencyVolume eur = volumes.stream().filter(v -> v.currency().equals("EUR")).findFirst().orElseThrow();

        assertThat(usd.transactionCount()).isEqualTo(2);
        assertThat(usd.totalAmount()).isEqualByComparingTo("25.00");
        assertThat(eur.transactionCount()).isEqualTo(1);
        assertThat(eur.totalAmount()).isEqualByComparingTo("100.00");
    }
}
