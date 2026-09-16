package com.swiftpay.ledger.repository;

import com.swiftpay.ledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    /**
     * SELECT ... FOR UPDATE. Callers MUST always lock the sender and
     * receiver accounts in a fixed order (lower user_id first) — see
     * LedgerTransactionService — to prevent two concurrent transfers
     * between the same pair of accounts from deadlocking each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.userId = :userId")
    Optional<Account> findByIdForUpdate(@Param("userId") String userId);
}
