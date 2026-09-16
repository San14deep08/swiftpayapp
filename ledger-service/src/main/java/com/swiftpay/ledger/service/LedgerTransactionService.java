package com.swiftpay.ledger.service;

import com.swiftpay.ledger.domain.Account;
import com.swiftpay.ledger.domain.Payment;
import com.swiftpay.ledger.domain.PaymentStatus;
import com.swiftpay.ledger.event.PaymentCompletedEvent;
import com.swiftpay.ledger.event.PaymentFailedEvent;
import com.swiftpay.ledger.event.PaymentInitiatedEvent;
import com.swiftpay.ledger.exception.PaymentNotFoundException;
import com.swiftpay.ledger.repository.AccountRepository;
import com.swiftpay.ledger.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Owns the one non-negotiable correctness guarantee in this system: the
 * debit and the credit either both happen or neither does, and two
 * concurrent transfers touching the same account can never both succeed
 * against a balance that can't cover them.
 *
 * Locking strategy: pessimistic (SELECT ... FOR UPDATE via
 * AccountRepository.findByIdForUpdate), not optimistic/@Version. Kafka
 * doesn't guarantee this topic is partitioned by account, so two transfers
 * touching the same account could be handled by different consumer
 * threads/instances; a DB-level row lock is the guarantee that holds
 * regardless of partitioning. Both accounts are always locked in a fixed
 * order (lower user_id first) to prevent A->B and B->A transfers from
 * deadlocking each other.
 *
 * Idempotent-consumer guard: Kafka's at-least-once delivery means this
 * method can be invoked more than once for the same event (e.g. a consumer
 * restart after processing but before committing its offset). Re-processing
 * is detected by the payment's status no longer being PENDING and is a
 * no-op, not an error — this is what actually prevents a duplicate debit,
 * not the Kafka offset commit alone.
 */
@Service
public class LedgerTransactionService {

    private static final Logger log = LoggerFactory.getLogger(LedgerTransactionService.class);

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public LedgerTransactionService(AccountRepository accountRepository,
                                     PaymentRepository paymentRepository,
                                     ApplicationEventPublisher applicationEventPublisher) {
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public void processPaymentInitiated(PaymentInitiatedEvent event) {
        Payment payment = paymentRepository.findByTransactionId(event.transactionId())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "No payment record for transaction_id " + event.transactionId()
                                + " — gateway-service should have created it as PENDING before publishing"));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("txn={} already in terminal state {} — skipping re-processing (Kafka redelivery)",
                    event.transactionId(), payment.getStatus());
            return;
        }

        // Lock both accounts in a fixed order to prevent deadlocks between
        // concurrent opposite-direction transfers on the same account pair.
        boolean senderFirst = event.senderId().compareTo(event.receiverId()) <= 0;
        String firstId = senderFirst ? event.senderId() : event.receiverId();
        String secondId = senderFirst ? event.receiverId() : event.senderId();

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new PaymentNotFoundException("Unknown account: " + firstId));
        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new PaymentNotFoundException("Unknown account: " + secondId));

        Account sender = senderFirst ? first : second;
        Account receiver = senderFirst ? second : first;

        if (!sender.hasAtLeast(event.amount())) {
            payment.markFailed("insufficient_funds");
            log.info("txn={} FAILED insufficient funds sender={} balance={} requested={}",
                    event.transactionId(), sender.getUserId(), sender.getBalance(), event.amount());
            applicationEventPublisher.publishEvent(new PaymentFailedEvent(
                    event.transactionId(), event.senderId(), event.receiverId(),
                    event.amount(), event.currency(), event.correlationId(),
                    "insufficient_funds", Instant.now()));
            return;
        }

        sender.debit(event.amount());
        receiver.credit(event.amount());
        payment.markCompleted();

        log.info("txn={} COMPLETED {} -> {} amount={}",
                event.transactionId(), sender.getUserId(), receiver.getUserId(), event.amount());

        applicationEventPublisher.publishEvent(new PaymentCompletedEvent(
                event.transactionId(), event.senderId(), event.receiverId(),
                event.amount(), event.currency(), event.correlationId(), Instant.now()));
    }
}
