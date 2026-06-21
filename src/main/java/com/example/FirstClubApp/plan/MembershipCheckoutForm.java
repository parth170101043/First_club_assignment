package com.example.FirstClubApp.plan;

import com.example.FirstClubApp.subscription.BillingCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Defines the member-facing mock payment form used to purchase a subscription.
 */
public record MembershipCheckoutForm(
    @NotNull UUID tierId,
    @NotNull BillingCycle billingCycle,
    @NotBlank @Size(max = 100) String cardholderName,
    @NotBlank @Size(max = 50) String brand,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9]{4}") String lastFour
) {
}
