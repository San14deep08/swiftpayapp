package com.swiftpay.analytics.dto;

import java.math.BigDecimal;

/**
 * Constructor shape must exactly match the JPQL "select new" expression in
 * PaymentEventRepository.volumeByCurrency().
 */
public record CurrencyVolume(String currency, long transactionCount, BigDecimal totalAmount) {
}
