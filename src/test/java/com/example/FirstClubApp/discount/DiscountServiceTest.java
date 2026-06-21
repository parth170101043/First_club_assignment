package com.example.FirstClubApp.discount;

import com.example.FirstClubApp.perk.Perk;
import com.example.FirstClubApp.perk.PerkType;
import com.example.FirstClubApp.perk.TierPerk;
import com.example.FirstClubApp.perk.TierPerkRepository;
import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.subscription.Subscription;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.tier.Tier;
import com.example.FirstClubApp.user.User;
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

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies best-perk discount selection for a user's current subscription.
 */
@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TIER_ID = UUID.randomUUID();

    @Mock
    private TierPerkRepository tierPerkRepository;

    @Mock
    private SubscriptionService subscriptionService;

    private DiscountService discountService;

    /**
     * Creates the discount evaluator with mocked subscription and perk dependencies.
     *
     * @return no return value
     * @implNote Used by JUnit before each perk discount test.
     */
    @BeforeEach
    void setUp() {
        discountService = new DiscountService(tierPerkRepository, subscriptionService);
    }

    /**
     * Verifies checkout chooses the perk producing the largest monetary discount.
     *
     * @return no return value
     * @implNote A lower percentage can win when the higher percentage has a restrictive cap.
     */
    @Test
    void appliesOnlyThePerkThatReducesTheBillMost() {
        Subscription subscription = prepareSubscription();
        when(subscriptionService.requireActiveSubscription(USER_ID)).thenReturn(subscription);
        TierPerk cappedTwentyPercent = discountAssignment(
            subscription, "CAPPED_20", "20.00", "100.00");
        TierPerk uncappedFifteenPercent = discountAssignment(
            subscription, "UNCAPPED_15", "15.00", null);
        when(tierPerkRepository.findAllByTierIdOrderByPerkNameAsc(TIER_ID))
            .thenReturn(List.of(cappedTwentyPercent, uncappedFifteenPercent));

        DiscountDtos.EvaluationResponse response = discountService.evaluate(
            USER_ID, new DiscountDtos.EvaluationRequest(new BigDecimal("1000.00")));

        assertThat(response.discountPercent()).isEqualByComparingTo("15.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("150.00");
        assertThat(response.finalAmount()).isEqualByComparingTo("850.00");
        assertThat(response.applied()).isTrue();
    }

    /**
     * Verifies discount evaluation uses the reusable perk configuration.
     *
     * @return no return value
     * @implNote Tier assignments no longer carry configuration overrides.
     */
    @Test
    void usesPerkConfiguration() {
        Subscription subscription = prepareSubscription();
        Perk perk = initialize(new Perk(
            "MEMBER_DISCOUNT", "Member discount", "Discount",
            PerkType.PERCENTAGE_DISCOUNT,
            Map.of("discountPercent", new BigDecimal("12.00"))), UUID.randomUUID());
        TierPerk assignment = initialize(
            new TierPerk(subscription.getTier(), perk), UUID.randomUUID());
        when(tierPerkRepository.findAllByTierIdOrderByPerkNameAsc(TIER_ID))
            .thenReturn(List.of(assignment));

        DiscountDtos.EvaluationResponse response = discountService.evaluate(
            subscription, new BigDecimal("500.00"));

        assertThat(response.discountPercent()).isEqualByComparingTo("12.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("60.00");
        assertThat(response.finalAmount()).isEqualByComparingTo("440.00");
        assertThat(response.appliedPerkCode()).isEqualTo("MEMBER_DISCOUNT");
        assertThat(response.appliedPerkName()).isEqualTo("Member discount");
    }

    /**
     * Verifies non-discount, inactive, and malformed perks do not affect the bill.
     *
     * @return no return value
     * @implNote Invalid perk configuration is ignored so checkout remains available.
     */
    @Test
    void ignoresIneligiblePerks() {
        Subscription subscription = prepareSubscription();
        Perk freeDelivery = initialize(new Perk(
            "FREE_DELIVERY", "Free delivery", "Delivery",
            PerkType.FREE_DELIVERY,
            Map.of("discountPercent", new BigDecimal("90.00"))), UUID.randomUUID());
        Perk malformed = initialize(new Perk(
            "BROKEN_DISCOUNT", "Broken discount", "Invalid",
            PerkType.PERCENTAGE_DISCOUNT,
            Map.of("discountPercent", "not-a-number")), UUID.randomUUID());
        TierPerk freeDeliveryAssignment = initialize(
            new TierPerk(subscription.getTier(), freeDelivery), UUID.randomUUID());
        TierPerk malformedAssignment = initialize(
            new TierPerk(subscription.getTier(), malformed), UUID.randomUUID());
        when(tierPerkRepository.findAllByTierIdOrderByPerkNameAsc(TIER_ID))
            .thenReturn(List.of(freeDeliveryAssignment, malformedAssignment));

        DiscountDtos.EvaluationResponse response = discountService.evaluate(
            subscription, new BigDecimal("500.00"));

        assertThat(response.discountAmount()).isZero();
        assertThat(response.finalAmount()).isEqualByComparingTo("500.00");
        assertThat(response.applied()).isFalse();
    }

    /**
     * Verifies the optional maximum discount limits one perk's calculated reduction.
     *
     * @return no return value
     * @implNote The configured percentage remains visible while the monetary amount is capped.
     */
    @Test
    void capsDiscountAtConfiguredMaximum() {
        Subscription subscription = prepareSubscription();
        TierPerk assignment = discountAssignment(
            subscription, "CAPPED_DISCOUNT", "25.00", "80.00");
        when(tierPerkRepository.findAllByTierIdOrderByPerkNameAsc(TIER_ID))
            .thenReturn(List.of(assignment));

        DiscountDtos.EvaluationResponse response = discountService.evaluate(
            subscription, new BigDecimal("1000.00"));

        assertThat(response.discountPercent()).isEqualByComparingTo("25.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("80.00");
        assertThat(response.finalAmount()).isEqualByComparingTo("920.00");
    }

    /**
     * Creates an active subscription fixture.
     *
     * @return active Gold subscription
     * @implNote Tests using the user-facing method wire this fixture through the service mock.
     */
    private Subscription prepareSubscription() {
        User user = initialize(
            new User("member@example.com", "Member", "User", null), USER_ID);
        Tier tier = initialize(new Tier(
            "GOLD", "Gold", "Gold tier", 2,
            new BigDecimal("299.00"), new BigDecimal("799.00"),
            new BigDecimal("2999.00")), TIER_ID);
        Subscription subscription = initialize(new Subscription(
            user, tier, BillingCycle.MONTHLY,
            Instant.parse("2026-06-21T06:30:00Z"),
            new BigDecimal("299.00"), "INR"), UUID.randomUUID());
        return subscription;
    }

    /**
     * Creates an active percentage-discount perk assignment.
     *
     * @param subscription subscription whose tier receives the perk
     * @param code unique perk code
     * @param percent configured percentage
     * @param maximum optional monetary cap
     * @return initialized assignment
     * @implNote Used by best-perk and cap tests.
     */
    private TierPerk discountAssignment(
        Subscription subscription,
        String code,
        String percent,
        String maximum
    ) {
        Map<String, Object> configuration = maximum == null
            ? Map.of("discountPercent", new BigDecimal(percent))
            : Map.of(
                "discountPercent", new BigDecimal(percent),
                "maximumDiscount", new BigDecimal(maximum)
            );
        Perk perk = initialize(new Perk(
            code, code, "Discount perk",
            PerkType.PERCENTAGE_DISCOUNT, configuration), UUID.randomUUID());
        return initialize(
            new TierPerk(subscription.getTier(), perk), UUID.randomUUID());
    }
}
