package com.swiftpay.gateway.service;

import com.swiftpay.gateway.domain.Account;
import com.swiftpay.gateway.domain.Payment;
import com.swiftpay.gateway.dto.PaymentRequest;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.event.PaymentInitiatedEvent;
import com.swiftpay.gateway.exception.AccountNotFoundException;
import com.swiftpay.gateway.repository.AccountRepository;
import com.swiftpay.gateway.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Split out from PaymentService specifically so @Transactional is applied
 * through Spring's proxy (a call from another bean), not via self-invocation
 * within the same class, which Spring's default proxy-based AOP cannot
 * intercept and would silently run without a transaction.
 *
 * Publishes to Kafka only via a Spring application event picked up by
 * PaymentInitiatedEventListener AFTER this transaction commits (see that
 * class). Publishing to Kafka directly inside this method would risk
 * emitting an event for a payment row that never actually commits, or vice
 * versa — the classic dual-write problem.
 */
@Service
public class PaymentPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PaymentPersistenceService.class);

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentPersistenceService(AccountRepository accountRepository,
                                      PaymentRepository paymentRepository,
                                      ApplicationEventPublisher applicationEventPublisher) {
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public PaymentResponse processNewPayment(PaymentRequest request) {
        String correlationId = UUID.randomUUID().toString();

        Account sender = accountRepository.findById(request.senderId())
                .orElseThrow(() -> new AccountNotFoundException("Unknown sender_id: " + request.senderId()));
        accountRepository.findById(request.receiverId())
                .orElseThrow(() -> new AccountNotFoundException("Unknown receiver_id: " + request.receiverId()));

        Payment payment = new Payment(
                request.transactionId(), request.senderId(), request.receiverId(),
                request.amount(), request.currency(), correlationId);

        // Fast, non-authoritative pre-check. The Ledger service performs the
        // authoritative check-and-debit under a row lock; balance may have
        // moved between this read and that write.
        if (sender.getBalance().compareTo(request.amount()) < 0) {
            payment.markFailed("insufficient_funds");
            paymentRepository.save(payment);
            log.info("Rejected txn={} sender={} insufficient funds (balance={}, requested={})",
                    request.transactionId(), request.senderId(), sender.getBalance(), request.amount());
            return PaymentResponse.from(payment);
        }

        paymentRepository.save(payment);

        applicationEventPublisher.publishEvent(new PaymentInitiatedEvent(
                request.transactionId(), request.senderId(), request.receiverId(),
                request.amount(), request.currency(), correlationId, Instant.now()));

        return PaymentResponse.from(payment);
    }
}
