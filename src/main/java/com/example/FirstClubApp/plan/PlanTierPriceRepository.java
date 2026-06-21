package com.example.FirstClubApp.plan;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.FirstClubApp.subscription.BillingCycle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence and lookup queries for billing-cycle and tier pricing.
 */
public interface PlanTierPriceRepository extends JpaRepository<PlanTierPrice, UUID> {

    /**
     * Finds active pricing for one billing cycle and tier.
     *
     * @param billingCycle duration enum
     * @param tierId tier UUID
     * @return active pricing, or an empty optional
     * @implNote Used by subscription creation, upgrade, and renewal.
     */
    @EntityGraph(attributePaths = {"tier"})
    Optional<PlanTierPrice> findByBillingCycleAndTierIdAndActiveTrue(
        BillingCycle billingCycle, UUID tierId);

    /**
     * Lists all active cycle-tier prices with required associations loaded.
     *
     * @return active pricing combinations
     * @implNote Used by the membership options catalogue.
     */
    @EntityGraph(attributePaths = {"tier"})
    List<PlanTierPrice> findAllByActiveTrue();

    /**
     * Lists pricing for one tier across all billing cycles.
     *
     * @param tierId tier UUID
     * @return pricing combinations for the tier
     * @implNote Used by focused admin tier pricing updates.
     */
    @EntityGraph(attributePaths = {"tier"})
    List<PlanTierPrice> findAllByTierIdAndActiveTrue(UUID tierId);
}
