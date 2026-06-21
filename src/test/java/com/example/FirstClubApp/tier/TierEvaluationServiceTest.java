package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.subscription.Subscription;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the current post-order tier reevaluation extension point.
 */
class TierEvaluationServiceTest {

    /**
     * Verifies active members retain their current tier until behavioral strategies are added.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect the order-triggered reevaluation hook.
     */
    @Test
    void returnsCurrentTierForActiveSubscription() {
        UUID userId = UUID.randomUUID();
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        Subscription subscription = subscription(userId);
        when(subscriptionService.findActiveSubscription(userId))
            .thenReturn(Optional.of(subscription));
        TierEvaluationService service = new TierEvaluationService(subscriptionService);

        String tierCode = service.reevaluate(userId);

        assertThat(tierCode).isEqualTo("GOLD");
    }

    /**
     * Verifies users without active subscriptions have no tier after reevaluation.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to enforce no-subscription means no tier.
     */
    @Test
    void returnsNoTierWithoutActiveSubscription() {
        UUID userId = UUID.randomUUID();
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(subscriptionService.findActiveSubscription(userId)).thenReturn(Optional.empty());
        TierEvaluationService service = new TierEvaluationService(subscriptionService);

        assertThat(service.reevaluate(userId)).isNull();
    }

    /**
     * Verifies the evaluator chooses the highest result across independent signals.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect current-tier calculation.
     */
    @Test
    void appliesHighestBehavioralTierAcrossStrategies() {
        UUID userId = UUID.randomUUID();
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        Subscription subscription = subscription(userId);
        Tier gold = tier("GOLD", 2);
        Tier platinum = tier("PLATINUM", 3);
        BehavioralTierStrategy goldStrategy = mock(BehavioralTierStrategy.class);
        BehavioralTierStrategy platinumStrategy = mock(BehavioralTierStrategy.class);
        when(subscriptionService.findActiveSubscription(userId))
            .thenReturn(Optional.of(subscription));
        when(goldStrategy.evaluate(
            org.mockito.ArgumentMatchers.any(User.class),
            org.mockito.ArgumentMatchers.any(Instant.class)))
            .thenReturn(Optional.of(gold));
        when(platinumStrategy.evaluate(
            org.mockito.ArgumentMatchers.any(User.class),
            org.mockito.ArgumentMatchers.any(Instant.class)))
            .thenReturn(Optional.of(platinum));
        TierEvaluationService service = new TierEvaluationService(
            subscriptionService,
            List.of(goldStrategy, platinumStrategy),
            Clock.fixed(Instant.parse("2026-06-21T06:30:00Z"), ZoneOffset.UTC));

        assertThat(service.reevaluate(userId)).isEqualTo("PLATINUM");
        assertThat(subscription.getComputedBehavioralTier()).isEqualTo(platinum);
        assertThat(subscription.getCurrentTier()).isEqualTo(platinum);
    }

    /**
     * Creates an active Gold subscription fixture.
     *
     * @param userId user UUID associated with the subscription
     * @return initialized active subscription
     * @implNote Used internally by tier reevaluation tests.
     */
    private Subscription subscription(UUID userId) {
        User user = initialize(
            new User("member@example.com", "Member", "User", null), userId);
        Tier tier = initialize(new Tier(
            "GOLD", "Gold", "Gold tier", 2,
            new BigDecimal("299.00"), new BigDecimal("799.00"),
            new BigDecimal("2999.00")), UUID.randomUUID());
        return initialize(new Subscription(
            user, tier, BillingCycle.MONTHLY,
            Instant.parse("2026-06-21T06:30:00Z"),
            new BigDecimal("299.00"), "INR"), UUID.randomUUID());
    }

    /**
     * Creates an initialized tier fixture.
     *
     * @param code tier code
     * @param rank tier rank
     * @return active tier
     * @implNote Used internally by multi-strategy evaluation tests.
     */
    private Tier tier(String code, int rank) {
        return initialize(new Tier(
            code, code, code + " tier", rank,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE), UUID.randomUUID());
    }
}
