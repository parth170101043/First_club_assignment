package com.example.FirstClubApp.home;

import com.example.FirstClubApp.perk.PerkDtos;
import com.example.FirstClubApp.perk.PerkService;
import com.example.FirstClubApp.subscription.SubscriptionDtos;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.user.UserDtos;
import com.example.FirstClubApp.user.UserService;
import com.example.FirstClubApp.tier.TierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Aggregates user, active subscription, and perk information for the HTML homepage.
 */
@Service
public class HomePageService {

    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final PerkService perkService;
    private final TierService tierService;

    /**
     * Creates the homepage aggregation service.
     *
     * @param userService service that provides the displayed user profile
     * @param subscriptionService service that provides the active subscription
     * @param perkService service that resolves active perks for the subscription tier
     * @return an initialized homepage service
     * @implNote Used by Spring dependency injection when constructing the homepage controller.
     */
    @Autowired
    public HomePageService(UserService userService,
                           SubscriptionService subscriptionService,
                           PerkService perkService,
                           TierService tierService) {
        this.userService = userService;
        this.subscriptionService = subscriptionService;
        this.perkService = perkService;
        this.tierService = tierService;
    }

    public HomePageService(UserService userService,
                           SubscriptionService subscriptionService,
                           PerkService perkService) {
        this(userService, subscriptionService, perkService, null);
    }

    /**
     * Builds the homepage model for a subscribed user.
     *
     * @param userId user UUID supplied by the homepage route
     * @return presentation-ready user, subscription, upgrade, and perk data
     * @implNote Used by {@link HomePageController#show(UUID, org.springframework.ui.Model)}.
     */
    public HomePageView getHomePage(UUID userId) {
        UserDtos.Response user = userService.findById(userId);
        SubscriptionDtos.Response subscription = subscriptionService.currentForUser(userId);
        PerkDtos.UserPerksResponse userPerks = perkService.getUserPerks(userId);
        return HomePageView.from(
            user,
            subscription,
            tierService == null ? java.util.List.of() : tierService.findActive(),
            userPerks.perks()
        );
    }

    /**
     * Builds the homepage for the account represented by the authenticated session.
     *
     * @param email authenticated login email
     * @return presentation-ready homepage data
     */
    public HomePageView getHomePageForEmail(String email) {
        return getHomePage(userService.requireUserByEmail(email).getId());
    }

    public void scheduleDowngrade(String email, UUID tierId) {
        UUID userId = userService.requireUserByEmail(email).getId();
        SubscriptionDtos.Response subscription =
            subscriptionService.currentForUser(userId);
        subscriptionService.downgrade(
            subscription.id(), new SubscriptionDtos.DowngradeRequest(tierId));
    }

    public void scheduleCancellation(String email) {
        UUID userId = userService.requireUserByEmail(email).getId();
        SubscriptionDtos.Response subscription =
            subscriptionService.currentForUser(userId);
        subscriptionService.cancel(subscription.id());
    }

    public void resumeAutoRenewal(String email) {
        UUID userId = userService.requireUserByEmail(email).getId();
        SubscriptionDtos.Response subscription =
            subscriptionService.currentForUser(userId);
        subscriptionService.reactivate(subscription.id());
    }
}
