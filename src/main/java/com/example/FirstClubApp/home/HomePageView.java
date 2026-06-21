package com.example.FirstClubApp.home;

import com.example.FirstClubApp.perk.PerkDtos;
import com.example.FirstClubApp.subscription.SubscriptionDtos;
import com.example.FirstClubApp.tier.TierDtos;
import com.example.FirstClubApp.user.UserDtos;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Defines the presentation-ready data rendered by the user membership homepage.
 */
public record HomePageView(
    UUID userId,
    UUID subscriptionId,
    String firstName,
    String tierCode,
    String tierName,
    String paidTierName,
    String billingCycle,
    String expiryDate,
    boolean cancellationScheduled,
    boolean upgradeAvailable,
    String scheduledDowngradeTierName,
    List<DowngradeOption> downgradeOptions,
    List<PerkDtos.AssignmentResponse> perks
) {

    private static final DateTimeFormatter EXPIRY_DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    /**
     * Builds a homepage model from the existing user, subscription, and entitlement responses.
     *
     * @param user user profile displayed in the welcome heading
     * @param subscription active subscription displayed in the summary card
     * @param perks active tier entitlements displayed in the perk section; defaults to an empty list
     * @return immutable presentation-ready homepage data
     * @implNote Used by {@link HomePageService} before rendering the Thymeleaf template.
     */
    public static HomePageView from(UserDtos.Response user,
                                    SubscriptionDtos.Response subscription,
                                    List<TierDtos.Response> activeTiers,
                                    List<PerkDtos.AssignmentResponse> perks) {
        boolean upgradeAvailable =
            !"PLATINUM".equalsIgnoreCase(subscription.minTierCode());
        int paidRank = activeTiers.stream()
            .filter(tier -> tier.id().equals(subscription.minTierId()))
            .mapToInt(TierDtos.Response::rank)
            .findFirst()
            .orElse(Integer.MIN_VALUE);
        List<DowngradeOption> downgradeOptions = activeTiers.stream()
            .filter(tier -> tier.rank() < paidRank)
            .map(tier -> new DowngradeOption(tier.id(), tier.name()))
            .toList();
        String scheduledDowngradeTierName = activeTiers.stream()
            .filter(tier -> tier.id().equals(subscription.scheduledMinTierId()))
            .map(TierDtos.Response::name)
            .findFirst()
            .orElse(subscription.scheduledMinTierCode());
        return new HomePageView(
            user.id(),
            subscription.id(),
            user.firstName(),
            subscription.currentTierCode(),
            subscription.currentTierName(),
            subscription.minTierName(),
            subscription.planName(),
            EXPIRY_DATE_FORMAT.format(subscription.expiresAt()),
            subscription.cancelAtPeriodEnd(),
            upgradeAvailable,
            scheduledDowngradeTierName,
            downgradeOptions,
            perks
        );
    }

    public record DowngradeOption(UUID tierId, String tierName) {
    }
}
