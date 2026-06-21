package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.subscription.Subscription;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.subscription.SubscriptionRepository;
import com.example.FirstClubApp.subscription.SubscriptionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Combines independent behavioral signals and updates the subscription effective tier.
 */
@Service
public class TierEvaluationService {

    private final SubscriptionService subscriptionService;
    private final List<BehavioralTierStrategy> strategies;
    private final Clock clock;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Creates the behavioral tier evaluation service.
     *
     * @param subscriptionService active subscription lookup service
     * @param strategies independent behavioral signals; defaults to all Spring strategy beans
     * @param clock UTC time source
     * @return an initialized tier evaluation service
     * @implNote Used by Spring and invoked by order creation.
     */
    @Autowired
    public TierEvaluationService(
        SubscriptionService subscriptionService,
        List<BehavioralTierStrategy> strategies,
        Clock clock,
        SubscriptionRepository subscriptionRepository
    ) {
        this.subscriptionService = subscriptionService;
        this.strategies = strategies;
        this.clock = clock;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Creates a compatibility evaluator without behavioral strategies.
     *
     * @param subscriptionService active subscription lookup service
     * @return evaluator that preserves the paid tier
     * @implNote Retained for existing isolated tests.
     */
    public TierEvaluationService(SubscriptionService subscriptionService) {
        this(subscriptionService, List.of(), Clock.systemUTC(), null);
    }

    public TierEvaluationService(
        SubscriptionService subscriptionService,
        List<BehavioralTierStrategy> strategies,
        Clock clock
    ) {
        this(subscriptionService, strategies, clock, null);
    }

    /**
     * Reevaluates all behavioral signals and applies their highest earned tier.
     *
     * @param userId user whose order or profile behavior changed
     * @return effective current tier code, or {@code null} without an active subscription
     * @implNote Used by {@code OrderService}; current tier remains at least the paid minimum.
     */
    @Transactional
    public String reevaluate(UUID userId) {
        return subscriptionService.findActiveSubscription(userId)
            .map(this::applyHighestBehavioralTier)
            .map(subscription -> subscription.getCurrentTier().getCode())
            .orElse(null);
    }

    /**
     * Reevaluates all active subscriptions after an administrator changes the rules.
     *
     * @return number of active subscriptions evaluated
     */
    @Transactional
    public int reevaluateAllActive() {
        if (subscriptionRepository == null) {
            return 0;
        }
        List<Subscription> active =
            subscriptionRepository.findAllByStatus(SubscriptionStatus.ACTIVE);
        active.forEach(this::applyHighestBehavioralTier);
        return active.size();
    }

    /**
     * Selects the highest result across all independent strategies.
     *
     * @param subscription active subscription being reevaluated
     * @return the same subscription after applying its behavioral tier
     * @implNote Used internally by {@link #reevaluate(UUID)}.
     */
    private Subscription applyHighestBehavioralTier(Subscription subscription) {
        Tier highest = strategies.stream()
            .map(strategy -> strategy.evaluate(
                subscription.getUser(), clock.instant()))
            .flatMap(java.util.Optional::stream)
            .max(Comparator.comparingInt(Tier::getRank))
            .orElse(null);
        subscription.applyBehavioralTier(highest);
        return subscription;
    }
}
