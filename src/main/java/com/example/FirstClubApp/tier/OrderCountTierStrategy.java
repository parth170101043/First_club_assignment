package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.order.OrderRepository;
import com.example.FirstClubApp.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Awards behavioral tiers from the user's order count in the current UTC calendar month.
 */
@Component
public class OrderCountTierStrategy implements BehavioralTierStrategy {

    private final OrderRepository orderRepository;
    private final TierService tierService;
    private final long goldThreshold;
    private final long platinumThreshold;
    private final BehavioralTierSettingsService settingsService;

    /**
     * Creates the order-count strategy with configurable thresholds.
     *
     * @param orderRepository completed-order aggregate gateway
     * @param tierService active tier lookup service
     * @param goldThreshold monthly order count required for Gold; defaults to {@code 5}
     * @param platinumThreshold monthly order count required for Platinum; defaults to {@code 10}
     * @return an initialized strategy
     * @implNote Used by Spring while constructing behavioral tier evaluation.
     */
    @Autowired
    public OrderCountTierStrategy(
        OrderRepository orderRepository,
        TierService tierService,
        BehavioralTierSettingsService settingsService
    ) {
        this.orderRepository = orderRepository;
        this.tierService = tierService;
        this.settingsService = settingsService;
        this.goldThreshold = 0;
        this.platinumThreshold = 0;
    }

    public OrderCountTierStrategy(
        OrderRepository orderRepository,
        TierService tierService,
        long goldThreshold,
        long platinumThreshold
    ) {
        this.orderRepository = orderRepository;
        this.tierService = tierService;
        this.settingsService = null;
        this.goldThreshold = goldThreshold;
        this.platinumThreshold = platinumThreshold;
    }

    /**
     * Evaluates the user's current-month order count.
     *
     * @param user active subscription owner
     * @param evaluatedAt evaluation instant
     * @return Platinum, Gold, or no earned tier
     * @implNote Used by {@link TierEvaluationService} after order creation.
     */
    @Override
    public Optional<Tier> evaluate(User user, Instant evaluatedAt) {
        Instant monthStart = evaluatedAt.atZone(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
        long orderCount = orderRepository
            .countByUserIdAndCreatedAtGreaterThanEqual(user.getId(), monthStart);
        BehavioralTierSettingsService.View settings =
            settingsService == null ? null : settingsService.current();
        long effectiveGold = settings == null
            ? goldThreshold : settings.goldOrderCount();
        long effectivePlatinum = settings == null
            ? platinumThreshold : settings.platinumOrderCount();
        if (orderCount >= effectivePlatinum) {
            return Optional.of(tierService.requireActiveTierByCode("PLATINUM"));
        }
        if (orderCount >= effectiveGold) {
            return Optional.of(tierService.requireActiveTierByCode("GOLD"));
        }
        return Optional.empty();
    }
}
