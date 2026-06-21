package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.order.OrderRepository;
import com.example.FirstClubApp.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies order-count, monthly-spend, and cohort behavioral tier signals independently.
 */
class BehavioralTierStrategyTest {

    private static final Instant NOW = Instant.parse("2026-06-21T06:30:00Z");

    /**
     * Verifies the order-count strategy awards the highest crossed threshold.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect order-count behavioral evaluation.
     */
    @Test
    void awardsPlatinumForHighMonthlyOrderCount() {
        User user = user(null);
        Tier platinum = tier("PLATINUM", 3);
        OrderRepository orderRepository = mock(OrderRepository.class);
        TierService tierService = mock(TierService.class);
        when(orderRepository.countByUserIdAndCreatedAtGreaterThanEqual(
            eq(user.getId()), any(Instant.class))).thenReturn(10L);
        when(tierService.requireActiveTierByCode("PLATINUM"))
            .thenReturn(platinum);
        OrderCountTierStrategy strategy = new OrderCountTierStrategy(
            orderRepository, tierService, 5, 10);

        assertThat(strategy.evaluate(user, NOW))
            .contains(platinum);
    }

    /**
     * Verifies the monthly-spend strategy awards Gold at its configured threshold.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect monthly-spend behavioral evaluation.
     */
    @Test
    void awardsGoldForMonthlySpend() {
        User user = user(null);
        Tier gold = tier("GOLD", 2);
        OrderRepository orderRepository = mock(OrderRepository.class);
        TierService tierService = mock(TierService.class);
        when(orderRepository.sumTotalAmountSince(
            eq(user.getId()), any(Instant.class)))
            .thenReturn(new BigDecimal("7000.00"));
        when(tierService.requireActiveTierByCode("GOLD")).thenReturn(gold);
        MonthlySpendTierStrategy strategy = new MonthlySpendTierStrategy(
            orderRepository, tierService,
            new BigDecimal("5000"), new BigDecimal("15000"));

        assertThat(strategy.evaluate(user, NOW))
            .contains(gold);
    }

    /**
     * Verifies cohort evaluation is independent of order history.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect cohort behavioral evaluation.
     */
    @Test
    void awardsConfiguredCohortTier() {
        User user = user("VIP");
        Tier platinum = tier("PLATINUM", 3);
        TierService tierService = mock(TierService.class);
        when(tierService.requireActiveTierByCode("PLATINUM"))
            .thenReturn(platinum);
        CohortTierStrategy strategy = new CohortTierStrategy(
            tierService, "EARLY_ADOPTER", "VIP");

        assertThat(strategy.evaluate(user, NOW))
            .contains(platinum);
    }

    /**
     * Creates an initialized user fixture.
     *
     * @param cohort optional behavioral cohort; defaults to {@code null}
     * @return enabled user
     * @implNote Used internally by behavioral strategy tests.
     */
    private User user(String cohort) {
        return initialize(new User(
            "member@example.com", "Member", "User", cohort), UUID.randomUUID());
    }

    /**
     * Creates an initialized tier fixture.
     *
     * @param code tier code
     * @param rank tier rank
     * @return active tier
     * @implNote Used internally by behavioral strategy tests.
     */
    private Tier tier(String code, int rank) {
        return initialize(new Tier(
            code, code, code + " tier", rank,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE), UUID.randomUUID());
    }
}
