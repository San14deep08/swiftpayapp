package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByTransactionId(String transactionId);

    Page<Payment> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(
            String senderId, String receiverId, Pageable pageable);
}
