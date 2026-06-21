package com.example.FirstClubApp.admin;

import com.example.FirstClubApp.tier.TierDtos;
import com.example.FirstClubApp.tier.TierService;
import com.example.FirstClubApp.plan.MembershipPlanService;
import com.example.FirstClubApp.plan.PlanDtos;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes focused administrative operations for membership tier configuration.
 */
@RestController
@RequestMapping("/api/v1/admin/tiers")
public class AdminTierController {

    private final TierService tierService;
    private final MembershipPlanService planService;

    /**
     * Creates the administrative tier controller.
     *
     * @param tierService service that owns tier rules and persistence
     * @param planService service that owns plan-tier pricing
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    @Autowired
    public AdminTierController(TierService tierService,
                               MembershipPlanService planService) {
        this.tierService = tierService;
        this.planService = planService;
    }

    /**
     * Creates a compatibility controller without explicit plan pricing support.
     *
     * @param tierService legacy tier pricing service
     * @return a compatibility controller
     * @implNote Production dependency injection uses the complete constructor.
     */
    public AdminTierController(TierService tierService) {
        this(tierService, null);
    }

    // /**
    //  * Updates Monthly, Quarterly, and Yearly prices used by future subscriptions.
    //  *
    //  * @param tierId tier UUID supplied in the URL path
    //  * @param request validated replacement prices parsed from JSON
    //  * @return tier response containing the updated prices
    //  * @implNote Used by administrators changing subscription prices without editing other tier fields.
    //  */
    // @PatchMapping("/{tierId}/prices")
    // TierDtos.Response updatePrices(
    //     @PathVariable UUID tierId,
    //     @Valid @RequestBody TierDtos.PriceUpdateRequest request) {
    //     return tierService.updatePrices(tierId, request);
    // }

    /**
     * Updates Monthly, Quarterly, and Yearly subscription prices for one tier.
     *
     * @param tierId paid tier UUID
     * @param request three duration prices and currency
     * @return updated plan-tier options
     * @implNote Used for simple pricing such as monthly 100, quarterly 250, yearly 850.
     */
    @PatchMapping("/{tierId}/subscription-prices")
    java.util.List<PlanDtos.OptionResponse> updateDurationPrices(
        @PathVariable UUID tierId,
        @Valid @RequestBody PlanDtos.DurationPricingRequest request) {
        tierService.requireTier(tierId);
        return planService.updateDurationPrices(tierId, request);
    }
}
