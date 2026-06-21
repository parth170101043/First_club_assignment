package com.example.FirstClubApp.subscription;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies calendar-month expiry behavior for every supported billing cycle.
 */
class BillingCycleTest {

    private static final Instant START = Instant.parse("2026-01-31T10:15:30Z");

    /**
     * Verifies that a month-end monthly subscription expires on the valid next month-end date.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to guard monthly expiry calculations.
     */
    @Test
    void calculatesMonthlyExpiryUsingCalendarMonths() {
        assertThat(BillingCycle.MONTHLY.expiryFrom(START))
            .isEqualTo(Instant.parse("2026-02-28T10:15:30Z"));
    }

    /**
     * Verifies quarterly and yearly calendar-month expiry calculations.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to guard longer billing-cycle calculations.
     */
    @Test
    void calculatesQuarterlyAndYearlyExpiry() {
        assertThat(BillingCycle.QUARTERLY.expiryFrom(START))
            .isEqualTo(Instant.parse("2026-04-30T10:15:30Z"));
        assertThat(BillingCycle.YEARLY.expiryFrom(START))
            .isEqualTo(Instant.parse("2027-01-31T10:15:30Z"));
    }
}
