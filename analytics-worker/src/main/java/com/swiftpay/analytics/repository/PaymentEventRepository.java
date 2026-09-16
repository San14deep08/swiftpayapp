package com.swiftpay.analytics.repository;

import com.swiftpay.analytics.domain.PaymentEvent;
import com.swiftpay.analytics.dto.CurrencyVolume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    boolean existsByTransactionId(String transactionId);

    /**
     * Per-currency volume summary — the "real-time volume monitoring" the
     * spec asks this service to support.
     */
    @Query("select new com.swiftpay.analytics.dto.CurrencyVolume(pe.currency, count(pe), sum(pe.amount)) "
            + "from PaymentEvent pe group by pe.currency order by pe.currency")
    List<CurrencyVolume> volumeByCurrency();
}
