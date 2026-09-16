package com.swiftpay.ledger.service;

import com.swiftpay.ledger.domain.Account;
import com.swiftpay.ledger.domain.Payment;
import com.swiftpay.ledger.domain.PaymentStatus;
import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the two non-negotiable correctness rules from the project spec:
 * atomicity (debit and credit both happen or neither does) and no
 * double-spend under concurrency (row-level locking).
 *
 * Runs against a real Postgres via Testcontainers, calling
 * LedgerTransactionService directly rather than through Kafka — this
 * exercises the actual transactional/locking logic without the added
 * complexity of a Kafka broker in the test, which the consumer-facing
 * behavior (retry/backoff/DLQ) doesn't need in order to be verified here.
 *
 * NOTE: EventPublisher is @MockBean'd out because it needs a real Kafka
 * broker to actually send — this test verifies the DB-transaction-level
 * correctness, not the Kafka publish itself. The Kafka listener container
 * (PaymentInitiatedListener) will still try to connect to localhost:9092
 * in the background and log connection failures since no broker is
 * running here; this is noisy but does not fail the test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class LedgerTransactionServiceTest {

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
    private LedgerTransactionService ledgerTransactionService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private EventPublisher eventPublisher;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE payments, accounts");
    }

    private void seedAccount(String userId, String balance) {
        jdbcTemplate.update(
                "INSERT INTO accounts (user_id, balance, currency) VALUES (?, ?, 'USD')",
                userId, new BigDecimal(balance));
    }

    private void seedPendingPayment(String transactionId, String senderId, String receiverId,
                                     String amount, String correlationId) {
        jdbcTemplate.update(
                "INSERT INTO payments (transaction_id, sender_id, receiver_id, amount, currency, status, correlation_id) "
                        + "VALUES (?, ?, ?, ?, 'USD', 'PENDING', ?)",
                transactionId, senderId, receiverId, new BigDecimal(amount), correlationId);
    }

    private void seedCompletedPayment(String transactionId, String senderId, String receiverId,
                                       String amount, String correlationId) {
        jdbcTemplate.update(
                "INSERT INTO payments (transaction_id, sender_id, receiver_id, amount, currency, status, correlation_id) "
                        + "VALUES (?, ?, ?, ?, 'USD', 'COMPLETED', ?)",
                transactionId, senderId, receiverId, new BigDecimal(amount), correlationId);
    }

    private PaymentInitiatedEvent eventFor(String transactionId, String senderId, String receiverId, String amount) {
        return new PaymentInitiatedEvent(transactionId, senderId, receiverId,
                new BigDecimal(amount), "USD", "corr-" + transactionId, Instant.now());
    }

    @Test
    void happyPath_debitsSenderAndCreditsReceiver_atomically() {
        seedAccount("alice", "100.00");
        seedAccount("bob", "0.00");
        seedPendingPayment("txn-1", "alice", "bob", "40.00", "corr-1");

        ledgerTransactionService.processPaymentInitiated(eventFor("txn-1", "alice", "bob", "40.00"));

        Account alice = accountRepository.findById("alice").orElseThrow();
        Account bob = accountRepository.findById("bob").orElseThrow();
        Payment payment = paymentRepository.findByTransactionId("txn-1").orElseThrow();

        assertThat(alice.getBalance()).isEqualByComparingTo("60.00");
        assertThat(bob.getBalance()).isEqualByComparingTo("40.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(eventPublisher).publishCompleted(any());
        verify(eventPublisher, never()).publishFailed(any());
    }

    @Test
    void insufficientFunds_marksFailed_andLeavesBalancesUntouched() {
        seedAccount("alice", "10.00");
        seedAccount("bob", "0.00");
        seedPendingPayment("txn-2", "alice", "bob", "50.00", "corr-2");

        ledgerTransactionService.processPaymentInitiated(eventFor("txn-2", "alice", "bob", "50.00"));

        Account alice = accountRepository.findById("alice").orElseThrow();
        Account bob = accountRepository.findById("bob").orElseThrow();
        Payment payment = paymentRepository.findByTransactionId("txn-2").orElseThrow();

        // The whole point of atomicity here: a failed payment must leave
        // BOTH balances exactly as they were, not a partial debit.
        assertThat(alice.getBalance()).isEqualByComparingTo("10.00");
        assertThat(bob.getBalance()).isEqualByComparingTo("0.00");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("insufficient_funds");
        verify(eventPublisher).publishFailed(any());
        verify(eventPublisher, never()).publishCompleted(any());
    }

    @Test
    void redeliveredEvent_forAlreadyCompletedPayment_isNoOp_notADoubleDebit() {
        seedAccount("alice", "60.00");
        seedAccount("bob", "40.00"); // reflects a payment that "already happened"
        seedCompletedPayment("txn-3", "alice", "bob", "40.00", "corr-3");

        // Simulates Kafka at-least-once redelivery of the same event after
        // it was already fully processed.
        ledgerTransactionService.processPaymentInitiated(eventFor("txn-3", "alice", "bob", "40.00"));

        Account alice = accountRepository.findById("alice").orElseThrow();
        Account bob = accountRepository.findById("bob").orElseThrow();

        assertThat(alice.getBalance()).isEqualByComparingTo("60.00");
        assertThat(bob.getBalance()).isEqualByComparingTo("40.00");
        verify(eventPublisher, never()).publishCompleted(any());
        verify(eventPublisher, never()).publishFailed(any());
    }

    @Test
    void concurrentTransfersFromSameAccount_neverBothSucceed_whenBalanceCoversOnlyOne() throws InterruptedException {
        // alice can cover exactly ONE of these two 50.00 transfers, not both.
        seedAccount("alice", "50.00");
        seedAccount("bob", "0.00");
        seedAccount("carol", "0.00");
        seedPendingPayment("txn-4a", "alice", "bob", "50.00", "corr-4a");
        seedPendingPayment("txn-4b", "alice", "carol", "50.00", "corr-4b");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Runnable taskA = () -> {
            await(startLatch);
            ledgerTransactionService.processPaymentInitiated(eventFor("txn-4a", "alice", "bob", "50.00"));
            doneLatch.countDown();
        };
        Runnable taskB = () -> {
            await(startLatch);
            ledgerTransactionService.processPaymentInitiated(eventFor("txn-4b", "alice", "carol", "50.00"));
            doneLatch.countDown();
        };

        executor.submit(taskA);
        executor.submit(taskB);
        startLatch.countDown(); // release both threads at (as close to) the same instant
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("both concurrent transfers should finish, not deadlock").isTrue();

        Account alice = accountRepository.findById("alice").orElseThrow();
        List<Payment> results = List.of(
                paymentRepository.findByTransactionId("txn-4a").orElseThrow(),
                paymentRepository.findByTransactionId("txn-4b").orElseThrow());

        long completedCount = results.stream().filter(p -> p.getStatus() == PaymentStatus.COMPLETED).count();
        long failedCount = results.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();

        // The actual correctness assertion: exactly one succeeds, the other
        // fails on insufficient funds — never both, and never a negative
        // balance from two debits racing past a stale balance check.
        assertThat(completedCount).isEqualTo(1);
        assertThat(failedCount).isEqualTo(1);
        assertThat(alice.getBalance()).isEqualByComparingTo("0.00");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
