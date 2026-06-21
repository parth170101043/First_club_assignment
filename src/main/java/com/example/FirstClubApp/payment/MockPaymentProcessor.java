package com.example.FirstClubApp.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulates a synchronous external payment provider without moving real money.
 */
@Component
public class MockPaymentProcessor {

    /**
     * Processes a mock tokenized charge.
     *
     * @param providerToken mock token associated with the payment method
     * @param amount positive charge amount
     * @param currency three-letter currency code
     * @return deterministic mock processor result
     * @implNote Used by {@link PaymentService}; tokens beginning with {@code fail_} simulate failure.
     */
    public ProcessorResult charge(String providerToken,
                                  BigDecimal amount,
                                  String currency) {
        boolean failed = providerToken.toLowerCase().startsWith("fail_");
        return new ProcessorResult(
            failed ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED,
            "mock_pay_" + UUID.randomUUID(),
            failed ? "Mock processor declined the payment method." : null
        );
    }

    /**
     * Defines the final outcome returned by the mock processor.
     */
    public record ProcessorResult(
        PaymentStatus status,
        String providerReference,
        String failureReason
    ) {
    }
}
