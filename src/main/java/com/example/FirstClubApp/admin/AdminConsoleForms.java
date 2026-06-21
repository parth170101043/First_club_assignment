package com.example.FirstClubApp.admin;

import com.example.FirstClubApp.perk.PerkType;
import com.example.FirstClubApp.perk.PerkDtos;
import com.example.FirstClubApp.subscription.BillingCycle;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Groups simple browser forms used by the admin console.
 */
public final class AdminConsoleForms {

    private AdminConsoleForms() {
    }

    public record PerkForm(
        String code,
        String name,
        String description,
        PerkType type,
        BigDecimal discountPercent,
        BigDecimal maximumDiscount
    ) {
    }

    public record AssignmentForm(
        UUID tierId,
        UUID perkId
    ) {
    }

    public record SubscriptionForm(
        UUID userId,
        UUID tierId,
        BillingCycle billingCycle
    ) {
    }

    public record BehavioralRulesForm(
        long goldOrderCount,
        long platinumOrderCount,
        BigDecimal goldMonthlySpend,
        BigDecimal platinumMonthlySpend,
        String goldCohort,
        String platinumCohort
    ) {
    }

    public record AssignmentView(
        String tierName,
        PerkDtos.AssignmentResponse assignment
    ) {
    }
}
