package com.example.FirstClubApp.plan;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes selectable billing cycles and priced cycle-tier options.
 */
@RestController
@RequestMapping("/api/v1")
public class MembershipPlanController {

    private final MembershipPlanService planService;

    /**
     * Creates the membership plan controller.
     *
     * @param planService service that owns plan catalogue and pricing
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public MembershipPlanController(MembershipPlanService planService) {
        this.planService = planService;
    }

    /**
     * Lists Monthly, Quarterly, and Yearly billing-cycle values.
     *
     * @return supported duration choices
     * @implNote Used by subscription selection clients.
     */
    @GetMapping("/plans")
    List<PlanDtos.PlanResponse> plans() {
        return planService.findPlans();
    }

    /**
     * Lists purchasable plan and tier combinations with prices.
     *
     * @return active membership options
     * @implNote Used by subscription selection clients.
     */
    @GetMapping("/membership-options")
    List<PlanDtos.OptionResponse> options() {
        return planService.findOptions();
    }
}
