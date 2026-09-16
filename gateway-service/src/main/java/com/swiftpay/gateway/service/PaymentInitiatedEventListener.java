package com.swiftpay.gateway.service;

import com.swiftpay.gateway.event.PaymentInitiatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentInitiatedEventListener {

    private final EventPublisher eventPublisher;

    public PaymentInitiatedEventListener(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentInitiated(PaymentInitiatedEvent event) {
        eventPublisher.publishPaymentInitiated(event);
    }
}
