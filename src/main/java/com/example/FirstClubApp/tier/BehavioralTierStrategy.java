package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.user.User;

import java.time.Instant;
import java.util.Optional;

/**
 * Defines one independent signal that may earn a user a behavioral membership tier.
 */
public interface BehavioralTierStrategy {

    /**
     * Evaluates one behavioral signal for a user.
     *
     * @param user active subscription owner
     * @param evaluatedAt evaluation instant used for time-window calculations
     * @return earned tier, or an empty optional when this signal earns no tier
     * @implNote Used by {@link TierEvaluationService}, which selects the highest strategy result.
     */
    Optional<Tier> evaluate(User user, Instant evaluatedAt);
}
