package com.example.FirstClubApp.discount;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

/**
 * Evaluates configured order discounts for users with active subscriptions.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/discount")
public class UserDiscountController {

    private final DiscountService discountService;

    /**
     * Creates the user discount controller.
     *
     * @param discountService service that evaluates subscription discount rules
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public UserDiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    /**
     * Calculates the best single subscription-perk discount for an order amount.
     *
     * @param userId user UUID supplied in the URL path
     * @param request validated order amount parsed from JSON
     * @return calculated discount and final order amount
     * @implNote Used by checkout clients before order placement.
     */
    @PostMapping("/evaluate")
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    DiscountDtos.EvaluationResponse evaluate(
        @PathVariable UUID userId,
        @Valid @RequestBody DiscountDtos.EvaluationRequest request) {
        return discountService.evaluate(userId, request);
    }
}
