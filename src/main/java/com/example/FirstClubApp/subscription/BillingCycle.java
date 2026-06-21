package com.example.FirstClubApp.subscription;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Defines supported subscription periods and calendar-based expiry calculations.
 */
public enum BillingCycle {
    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12);

    private final int months;

    /**
     * Creates a billing-cycle constant.
     *
     * @param months number of calendar months represented by the cycle
     * @return an enum constant initialized with its duration
     * @implNote Used internally while Java initializes the enum constants.
     */
    BillingCycle(int months) {
        this.months = months;
    }

    /**
     * Calculates the subscription expiry from a start instant in UTC.
     *
     * @param startsAt subscription start instant with no default value
     * @return start instant advanced by this cycle's calendar-month duration
     * @implNote Used by {@link Subscription} when a new subscription is created.
     */
    public Instant expiryFrom(Instant startsAt) {
        return startsAt.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }
}
