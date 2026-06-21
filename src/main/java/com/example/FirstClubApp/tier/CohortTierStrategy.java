package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Awards behavioral tiers from configurable user cohort membership.
 */
@Component
public class CohortTierStrategy implements BehavioralTierStrategy {

    private final TierService tierService;
    private final String goldCohort;
    private final String platinumCohort;
    private final BehavioralTierSettingsService settingsService;

    /**
     * Creates the cohort strategy with configurable cohort names.
     *
     * @param tierService active tier lookup service
     * @param goldCohort cohort earning Gold; defaults to {@code EARLY_ADOPTER}
     * @param platinumCohort cohort earning Platinum; defaults to {@code VIP}
     * @return an initialized strategy
     * @implNote Used by Spring while constructing behavioral tier evaluation.
     */
    @Autowired
    public CohortTierStrategy(
        TierService tierService,
        BehavioralTierSettingsService settingsService
    ) {
        this.tierService = tierService;
        this.settingsService = settingsService;
        this.goldCohort = null;
        this.platinumCohort = null;
    }

    public CohortTierStrategy(
        TierService tierService,
        String goldCohort,
        String platinumCohort
    ) {
        this.tierService = tierService;
        this.settingsService = null;
        this.goldCohort = goldCohort;
        this.platinumCohort = platinumCohort;
    }

    /**
     * Evaluates the cohort stored on the user profile.
     *
     * @param user active subscription owner
     * @param evaluatedAt evaluation instant; cohort evaluation does not use time
     * @return Platinum, Gold, or no earned tier
     * @implNote Used by {@link TierEvaluationService} with other independent signals.
     */
    @Override
    public Optional<Tier> evaluate(User user, Instant evaluatedAt) {
        String cohort = user.getCohort();
        if (cohort == null) {
            return Optional.empty();
        }
        BehavioralTierSettingsService.View settings =
            settingsService == null ? null : settingsService.current();
        String effectiveGold = settings == null
            ? goldCohort : settings.goldCohort();
        String effectivePlatinum = settings == null
            ? platinumCohort : settings.platinumCohort();
        if (cohort.equalsIgnoreCase(effectivePlatinum)) {
            return Optional.of(tierService.requireActiveTierByCode("PLATINUM"));
        }
        if (cohort.equalsIgnoreCase(effectiveGold)) {
            return Optional.of(tierService.requireActiveTierByCode("GOLD"));
        }
        return Optional.empty();
    }
}
