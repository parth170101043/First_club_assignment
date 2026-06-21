package com.example.FirstClubApp.plan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.example.FirstClubApp.subscription.BillingCycle;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Groups public plan catalogue and administrative pricing contracts.
 */
public final class PlanDtos {

    /**
     * Prevents instantiation of this DTO namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime.
     */
    private PlanDtos() {
    }

    /**
     * Defines one selectable membership duration plan.
     */
    public record PlanResponse(
        BillingCycle billingCycle,
        String name,
        int durationMonths
    ) {
        /**
         * Maps a billing-cycle enum to its API representation.
         *
         * @param billingCycle supported duration enum
         * @return immutable billing-cycle response
         * @implNote Used by {@link MembershipPlanService}.
         */
        static PlanResponse from(BillingCycle billingCycle) {
            return new PlanResponse(
                billingCycle,
                switch (billingCycle) {
                    case MONTHLY -> "Monthly";
                    case QUARTERLY -> "Quarterly";
                    case YEARLY -> "Yearly";
                },
                switch (billingCycle) {
                    case MONTHLY -> 1;
                    case QUARTERLY -> 3;
                    case YEARLY -> 12;
                });
        }
    }

    /**
     * Defines one selectable plan and tier price combination.
     */
    public record OptionResponse(
        BillingCycle billingCycle,
        int durationMonths,
        UUID tierId,
        String tierCode,
        String tierName,
        int tierRank,
        BigDecimal price,
        String currency
    ) {
        /**
         * Maps persistent pricing to a selectable membership option.
         *
         * @param pricing active plan-tier pricing
         * @return immutable option response
         * @implNote Used by {@link MembershipPlanService}.
         */
        static OptionResponse from(PlanTierPrice pricing) {
            return new OptionResponse(
                pricing.getBillingCycle(),
                switch (pricing.getBillingCycle()) {
                    case MONTHLY -> 1;
                    case QUARTERLY -> 3;
                    case YEARLY -> 12;
                },
                pricing.getTier().getId(),
                pricing.getTier().getCode(),
                pricing.getTier().getName(),
                pricing.getTier().getRank(),
                pricing.getPrice(),
                pricing.getCurrency()
            );
        }
    }

    /**
     * Defines convenient Monthly, Quarterly, and Yearly prices for one paid tier.
     */
    public record DurationPricingRequest(
        @NotNull @DecimalMin("0.00") BigDecimal monthlyPrice,
        @NotNull @DecimalMin("0.00") BigDecimal quarterlyPrice,
        @NotNull @DecimalMin("0.00") BigDecimal yearlyPrice,
        @NotNull @Pattern(regexp = "[A-Za-z]{3}") String currency
    ) {
    }
}
