package com.example.FirstClubApp.payment;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies deterministic success and failure behavior of the mock payment provider.
 */
class MockPaymentProcessorTest {

    /**
     * Verifies normal mock tokens produce successful transactions.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect the standard payment demonstration.
     */
    @Test
    void succeedsForNormalToken() {
        MockPaymentProcessor processor = new MockPaymentProcessor();

        MockPaymentProcessor.ProcessorResult result = processor.charge(
            "tok_success", new BigDecimal("299.00"), "INR");

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.providerReference()).startsWith("mock_pay_");
        assertThat(result.failureReason()).isNull();
    }

    /**
     * Verifies tokens beginning with fail_ simulate a provider decline.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to provide a reproducible failed-payment scenario.
     */
    @Test
    void failsForFailureToken() {
        MockPaymentProcessor processor = new MockPaymentProcessor();

        MockPaymentProcessor.ProcessorResult result = processor.charge(
            "fail_card", new BigDecimal("299.00"), "INR");

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.failureReason()).contains("declined");
    }
}
