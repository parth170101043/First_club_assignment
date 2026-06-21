package com.example.FirstClubApp.home;

import com.example.FirstClubApp.perk.PerkDtos;
import com.example.FirstClubApp.perk.PerkService;
import com.example.FirstClubApp.perk.PerkType;
import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.subscription.SubscriptionDtos;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.subscription.SubscriptionStatus;
import com.example.FirstClubApp.user.UserDtos;
import com.example.FirstClubApp.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies homepage aggregation, upgrade visibility, expiry formatting, and displayed perks.
 */
@ExtendWith(MockitoExtension.class)
class HomePageServiceTest {

    private static final UUID USER_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TIER_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant EXPIRY = Instant.parse("2026-07-21T06:30:00Z");

    @Mock
    private UserService userService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private PerkService perkService;

    private HomePageService homePageService;

    /**
     * Creates the homepage service with mocked feature services.
     *
     * @return no return value
     * @implNote Used by JUnit before each homepage service test.
     */
    @BeforeEach
    void setUp() {
        homePageService = new HomePageService(userService, subscriptionService, perkService);
        when(userService.findById(USER_ID)).thenReturn(userResponse());
    }

    /**
     * Verifies non-Platinum members receive an upgrade option and active perk cards.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect Gold and Silver homepage behavior.
     */
    @Test
    void showsUpgradeOptionAndPerksForGoldMember() {
        when(subscriptionService.currentForUser(USER_ID))
            .thenReturn(subscriptionResponse("GOLD", "Gold"));
        when(perkService.getUserPerks(USER_ID)).thenReturn(userPerks("GOLD"));

        HomePageView home = homePageService.getHomePage(USER_ID);

        assertThat(home.firstName()).isEqualTo("Member");
        assertThat(home.upgradeAvailable()).isTrue();
        assertThat(home.expiryDate()).isEqualTo("21 Jul 2026");
        assertThat(home.perks()).hasSize(1);
    }

    /**
     * Verifies Platinum members do not receive an upgrade option.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect highest-tier homepage behavior.
     */
    @Test
    void hidesUpgradeOptionForPlatinumMember() {
        when(subscriptionService.currentForUser(USER_ID))
            .thenReturn(subscriptionResponse("PLATINUM", "Platinum"));
        when(perkService.getUserPerks(USER_ID)).thenReturn(userPerks("PLATINUM"));

        HomePageView home = homePageService.getHomePage(USER_ID);

        assertThat(home.upgradeAvailable()).isFalse();
        assertThat(home.tierName()).isEqualTo("Platinum");
    }

    /**
     * Creates the user profile displayed by the homepage.
     *
     * @return enabled user response with a stable identifier
     * @implNote Used internally by all homepage service tests.
     */
    private UserDtos.Response userResponse() {
        return new UserDtos.Response(
            USER_ID, "member@example.com", "Member", "User", null, true,
            Instant.parse("2026-06-01T06:30:00Z"));
    }

    /**
     * Creates an active subscription response for a requested tier.
     *
     * @param tierCode machine-readable tier code
     * @param tierName tier display name
     * @return active monthly subscription response
     * @implNote Used internally by Gold and Platinum homepage tests.
     */
    private SubscriptionDtos.Response subscriptionResponse(String tierCode, String tierName) {
        return new SubscriptionDtos.Response(
            SUBSCRIPTION_ID, USER_ID, "member@example.com", TIER_ID, tierCode, tierName,
            BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE,
            Instant.parse("2026-06-21T06:30:00Z"), EXPIRY, false, null,
            new BigDecimal("299.00"), "INR", 0);
    }

    /**
     * Creates one active perk assignment for the requested subscription tier.
     *
     * @param tierCode machine-readable tier code displayed by the user-perks response
     * @return user perk response containing one free-delivery entitlement
     * @implNote Used internally by all homepage service tests.
     */
    private PerkDtos.UserPerksResponse userPerks(String tierCode) {
        PerkDtos.AssignmentResponse perk = new PerkDtos.AssignmentResponse(
            UUID.randomUUID(), TIER_ID, UUID.randomUUID(), "FREE_DELIVERY",
            "Free delivery", "Free delivery on eligible orders", PerkType.FREE_DELIVERY,
            Map.of(), Map.of(), true);
        return new PerkDtos.UserPerksResponse(
            USER_ID, SUBSCRIPTION_ID, TIER_ID, tierCode, EXPIRY, List.of(perk));
    }
}
