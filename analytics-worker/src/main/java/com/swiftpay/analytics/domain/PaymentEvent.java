package com.swiftpay.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only row in the mock OLAP sink (a plain Postgres table standing in
 * for real ClickHouse — see README for the tradeoff). One row per
 * PaymentCompleted event; transaction_id has a unique constraint at the DB
 * level so a Kafka-redelivered event can't create a duplicate row even if
 * the application-level check in PaymentCompletedListener were ever
 * bypassed.
 */
@Entity
@Table(name = "payment_events", schema = "analytics")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, updatable = false)
    private String transactionId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private String senderId;

    @Column(name = "receiver_id", nullable = false, updatable = false)
    private String receiverId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    protected PaymentEvent() {
        // JPA
    }

    public PaymentEvent(String transactionId, String senderId, String receiverId,
                         BigDecimal amount, String currency, Instant completedAt) {
        this.transactionId = transactionId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.amount = amount;
        this.currency = currency;
        this.completedAt = completedAt;
        this.ingestedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
