package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.order.OrderRepository;
import com.example.FirstClubApp.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Awards behavioral tiers from the user's original order spend in the current UTC month.
 */
@Component
public class MonthlySpendTierStrategy implements BehavioralTierStrategy {

    private final OrderRepository orderRepository;
    private final TierService tierService;
    private final BigDecimal goldThreshold;
    private final BigDecimal platinumThreshold;
    private final BehavioralTierSettingsService settingsService;

    /**
     * Creates the monthly-spend strategy with configurable thresholds.
     *
     * @param orderRepository completed-order aggregate gateway
     * @param tierService active tier lookup service
     * @param goldThreshold monthly spend required for Gold; defaults to {@code 5000}
     * @param platinumThreshold monthly spend required for Platinum; defaults to {@code 15000}
     * @return an initialized strategy
     * @implNote Used by Spring while constructing behavioral tier evaluation.
     */
    @Autowired
    public MonthlySpendTierStrategy(
        OrderRepository orderRepository,
        TierService tierService,
        BehavioralTierSettingsService settingsService
    ) {
        this.orderRepository = orderRepository;
        this.tierService = tierService;
        this.settingsService = settingsService;
        this.goldThreshold = BigDecimal.ZERO;
        this.platinumThreshold = BigDecimal.ZERO;
    }

    public MonthlySpendTierStrategy(
        OrderRepository orderRepository,
        TierService tierService,
        BigDecimal goldThreshold,
        BigDecimal platinumThreshold
    ) {
        this.orderRepository = orderRepository;
        this.tierService = tierService;
        this.settingsService = null;
        this.goldThreshold = goldThreshold;
        this.platinumThreshold = platinumThreshold;
    }

    /**
     * Evaluates the user's current-month original order spend.
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
        BigDecimal spend = orderRepository.sumTotalAmountSince(
            user.getId(), monthStart);
        BehavioralTierSettingsService.View settings =
            settingsService == null ? null : settingsService.current();
        BigDecimal effectiveGold = settings == null
            ? goldThreshold : settings.goldMonthlySpend();
        BigDecimal effectivePlatinum = settings == null
            ? platinumThreshold : settings.platinumMonthlySpend();
        if (spend.compareTo(effectivePlatinum) >= 0) {
            return Optional.of(tierService.requireActiveTierByCode("PLATINUM"));
        }
        if (spend.compareTo(effectiveGold) >= 0) {
            return Optional.of(tierService.requireActiveTierByCode("GOLD"));
        }
        return Optional.empty();
    }
}
