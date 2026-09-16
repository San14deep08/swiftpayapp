package com.swiftpay.analytics.controller;

import com.swiftpay.analytics.dto.CurrencyVolume;
import com.swiftpay.analytics.repository.PaymentEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/analytics")
public class AnalyticsController {

    private final PaymentEventRepository paymentEventRepository;

    public AnalyticsController(PaymentEventRepository paymentEventRepository) {
        this.paymentEventRepository = paymentEventRepository;
    }

    @GetMapping("/volume")
    @Operation(summary = "Real-time completed-payment volume, grouped by currency",
            description = "Backed by the analytics.payment_events mock OLAP sink, populated by "
                    + "consuming PaymentCompleted events off Kafka.")
    public List<CurrencyVolume> volumeByCurrency() {
        return paymentEventRepository.volumeByCurrency();
    }
}
