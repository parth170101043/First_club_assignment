package com.example.FirstClubApp.plan;

import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.tier.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies enum-based billing-cycle catalogue and configurable tier prices.
 */
class MembershipPlanServiceTest {

    /**
     * Verifies options are ordered by billing cycle and then tier rank.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect membership selection ordering.
     */
    @Test
    void listsBillingCycleTierOptionsInDisplayOrder() {
        PlanTierPriceRepository repository = mock(PlanTierPriceRepository.class);
        MembershipPlanService service = new MembershipPlanService(repository);
        Tier silver = tier("SILVER", 1);
        Tier gold = tier("GOLD", 2);
        when(repository.findAllByActiveTrue()).thenReturn(List.of(
            price(BillingCycle.YEARLY, gold, "850.00"),
            price(BillingCycle.MONTHLY, gold, "100.00"),
            price(BillingCycle.MONTHLY, silver, "75.00")
        ));

        List<PlanDtos.OptionResponse> options = service.findOptions();

        assertThat(options)
            .extracting(option ->
                option.billingCycle() + "-" + option.tierCode())
            .containsExactly("MONTHLY-SILVER", "MONTHLY-GOLD", "YEARLY-GOLD");
        assertThat(service.findPlans())
            .extracting(PlanDtos.PlanResponse::billingCycle)
            .containsExactly(
                BillingCycle.MONTHLY,
                BillingCycle.QUARTERLY,
                BillingCycle.YEARLY);
    }

    /**
     * Verifies administrators can replace all three cycle prices for a tier.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect configurable subscription prices.
     */
    @Test
    void updatesAllBillingCyclePricesForTier() {
        UUID tierId = UUID.randomUUID();
        Tier gold = tier("GOLD", 2);
        PlanTierPriceRepository repository = mock(PlanTierPriceRepository.class);
        MembershipPlanService service = new MembershipPlanService(repository);
        PlanTierPrice monthly = price(BillingCycle.MONTHLY, gold, "1.00");
        PlanTierPrice quarterly = price(BillingCycle.QUARTERLY, gold, "1.00");
        PlanTierPrice yearly = price(BillingCycle.YEARLY, gold, "1.00");
        when(repository.findByBillingCycleAndTierIdAndActiveTrue(
            BillingCycle.MONTHLY, tierId)).thenReturn(java.util.Optional.of(monthly));
        when(repository.findByBillingCycleAndTierIdAndActiveTrue(
            BillingCycle.QUARTERLY, tierId)).thenReturn(java.util.Optional.of(quarterly));
        when(repository.findByBillingCycleAndTierIdAndActiveTrue(
            BillingCycle.YEARLY, tierId)).thenReturn(java.util.Optional.of(yearly));
        when(repository.findAllByTierIdAndActiveTrue(tierId))
            .thenReturn(List.of(yearly, monthly, quarterly));

        List<PlanDtos.OptionResponse> response = service.updateDurationPrices(
            tierId,
            new PlanDtos.DurationPricingRequest(
                new BigDecimal("100.00"),
                new BigDecimal("250.00"),
                new BigDecimal("850.00"),
                "inr"));

        assertThat(response)
            .extracting(PlanDtos.OptionResponse::price)
            .containsExactly(
                new BigDecimal("100.00"),
                new BigDecimal("250.00"),
                new BigDecimal("850.00"));
        assertThat(response)
            .extracting(PlanDtos.OptionResponse::currency)
            .containsOnly("INR");
        verify(repository).findAllByTierIdAndActiveTrue(tierId);
    }

    /**
     * Creates an initialized tier fixture.
     *
     * @param code tier code
     * @param rank tier rank
     * @return active tier
     * @implNote Used internally by pricing catalogue tests.
     */
    private Tier tier(String code, int rank) {
        return initialize(new Tier(
            code, code, code + " tier", rank,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE), UUID.randomUUID());
    }

    /**
     * Creates initialized cycle-tier pricing.
     *
     * @param billingCycle duration enum
     * @param tier paid tier
     * @param amount configured amount
     * @return active pricing fixture
     * @implNote Used internally by pricing catalogue tests.
     */
    private PlanTierPrice price(
        BillingCycle billingCycle, Tier tier, String amount) {
        return initialize(new PlanTierPrice(
            billingCycle, tier, new BigDecimal(amount), "INR"), UUID.randomUUID());
    }
}
