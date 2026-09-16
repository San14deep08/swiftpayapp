package com.swiftpay.gateway.repository;

import com.swiftpay.gateway.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}
