package com.example.FirstClubApp.plan;

import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.subscription.BillingCycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Manages billing-cycle catalogue retrieval and configurable tier pricing.
 */
@Service
public class MembershipPlanService {

    private final PlanTierPriceRepository priceRepository;

    /**
     * Creates the billing-cycle pricing service.
     *
     * @param priceRepository persistence gateway for cycle-tier pricing
     * @return an initialized service
     * @implNote Used by catalogue, subscription, and admin components.
     */
    public MembershipPlanService(PlanTierPriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    /**
     * Lists the supported billing-cycle enum values.
     *
     * @return Monthly, Quarterly, and Yearly choices
     * @implNote Used by the public plans endpoint without plan entities.
     */
    public List<PlanDtos.PlanResponse> findPlans() {
        return Arrays.stream(BillingCycle.values())
            .map(PlanDtos.PlanResponse::from)
            .toList();
    }

    /**
     * Lists purchasable billing-cycle and tier combinations.
     *
     * @return options ordered by cycle duration and tier rank
     * @implNote Used by subscription selection clients.
     */
    @Transactional(readOnly = true)
    public List<PlanDtos.OptionResponse> findOptions() {
        return priceRepository.findAllByActiveTrue().stream()
            .filter(price -> price.getTier().isActive())
            .sorted(Comparator
                .comparingInt((PlanTierPrice value) ->
                    value.getBillingCycle().ordinal())
                .thenComparing(value -> value.getTier().getRank()))
            .map(PlanDtos.OptionResponse::from)
            .toList();
    }

    /**
     * Loads active pricing for one billing cycle and tier.
     *
     * @param billingCycle selected duration
     * @param tierId tier UUID
     * @return active pricing combination
     * @implNote Used by subscription creation, upgrade, downgrade, and renewal.
     */
    public PlanTierPrice requirePrice(BillingCycle billingCycle, UUID tierId) {
        return priceRepository
            .findByBillingCycleAndTierIdAndActiveTrue(billingCycle, tierId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Active pricing not found for billing cycle and tier."));
    }

    /**
     * Updates Monthly, Quarterly, and Yearly prices for one paid tier.
     *
     * @param tierId paid tier UUID
     * @param request duration prices and currency
     * @return updated options ordered by duration
     * @implNote Used by the administrative subscription pricing endpoint.
     */
    @Transactional
    public List<PlanDtos.OptionResponse> updateDurationPrices(
        UUID tierId,
        PlanDtos.DurationPricingRequest request) {
        String currency = request.currency().toUpperCase(Locale.ROOT);
        requirePrice(BillingCycle.MONTHLY, tierId)
            .update(request.monthlyPrice(), currency);
        requirePrice(BillingCycle.QUARTERLY, tierId)
            .update(request.quarterlyPrice(), currency);
        requirePrice(BillingCycle.YEARLY, tierId)
            .update(request.yearlyPrice(), currency);
        return priceRepository.findAllByTierIdAndActiveTrue(tierId).stream()
            .sorted(Comparator.comparingInt(value ->
                value.getBillingCycle().ordinal()))
            .map(PlanDtos.OptionResponse::from)
            .toList();
    }
}
