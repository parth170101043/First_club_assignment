package com.example.FirstClubApp.order;

import com.example.FirstClubApp.discount.DiscountDtos;
import com.example.FirstClubApp.discount.DiscountService;
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
import java.util.Optional;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies checkout benefit snapshots for subscribed and unsubscribed users.
 */
@ExtendWith(MockitoExtension.class)
class OrderBenefitServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TIER_ID = UUID.randomUUID();

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private DiscountService discountService;

    @Mock
    private TierPerkRepository tierPerkRepository;

    private OrderBenefitService orderBenefitService;

    /**
     * Creates the checkout benefit service with mocked membership dependencies.
     *
     * @return no return value
     * @implNote Used by JUnit before each checkout benefit test.
     */
    @BeforeEach
    void setUp() {
        orderBenefitService = new OrderBenefitService(
            subscriptionService, discountService, tierPerkRepository);
    }

    /**
     * Verifies users without active subscriptions receive no membership benefits.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to enforce no-subscription means no tier or perks.
     */
    @Test
    void returnsNoBenefitsWithoutActiveSubscription() {
        when(subscriptionService.findActiveSubscription(USER_ID)).thenReturn(Optional.empty());

        OrderBenefitService.BenefitResult result = orderBenefitService.evaluate(
            USER_ID, new BigDecimal("1000.00"), "GROCERY");

        assertThat(result.discountPercent()).isZero();
        assertThat(result.deliveryFee()).isEqualByComparingTo("50.00");
        assertThat(result.finalAmount()).isEqualByComparingTo("1050.00");
        assertThat(result.freeDelivery()).isFalse();
        assertThat(result.membershipTierCode()).isNull();
    }

    /**
     * Verifies active subscriptions receive discount and free-delivery benefits.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect shopping integration behavior.
     */
    @Test
    void appliesCurrentMembershipBenefits() {
        Subscription subscription = subscription();
        Perk freeDelivery = initialize(new Perk(
            "FREE_DELIVERY", "Free delivery", "Free delivery",
            PerkType.FREE_DELIVERY, Map.of()), UUID.randomUUID());
        TierPerk assignment = initialize(
            new TierPerk(subscription.getTier(), freeDelivery), UUID.randomUUID());
        when(subscriptionService.findActiveSubscription(USER_ID))
            .thenReturn(Optional.of(subscription));
        when(discountService.evaluate(subscription, new BigDecimal("1000.00")))
            .thenReturn(new DiscountDtos.EvaluationResponse(
                USER_ID, subscription.getId(), TIER_ID,
                new BigDecimal("1000.00"), new BigDecimal("10.00"),
                new BigDecimal("100.00"), new BigDecimal("900.00"), true,
                "EXTRA_DISCOUNT", "Extra discount"));
        when(tierPerkRepository.findAllByTierIdOrderByPerkNameAsc(TIER_ID))
            .thenReturn(List.of(assignment));

        OrderBenefitService.BenefitResult result = orderBenefitService.evaluate(
            USER_ID, new BigDecimal("1000.00"), "GROCERY");

        assertThat(result.discountPercent()).isEqualByComparingTo("10.00");
        assertThat(result.deliveryFee()).isZero();
        assertThat(result.finalAmount()).isEqualByComparingTo("900.00");
        assertThat(result.appliedDiscountPerkCode()).isEqualTo("EXTRA_DISCOUNT");
        assertThat(result.appliedDiscountPerkName()).isEqualTo("Extra discount");
        assertThat(result.freeDelivery()).isTrue();
        assertThat(result.membershipTierCode()).isEqualTo("GOLD");
    }

    /**
     * Verifies a member still pays delivery when the tier lacks free delivery.
     */
    @Test
    void chargesDeliveryWhenMembershipHasNoFreeDeliveryPerk() {
        Subscription subscription = subscription();
        when(subscriptionService.findActiveSubscription(USER_ID))
            .thenReturn(Optional.of(subscription));
        when(discountService.evaluate(subscription, new BigDecimal("1000.00")))
            .thenReturn(new DiscountDtos.EvaluationResponse(
                USER_ID, subscription.getId(), TIER_ID,
                new BigDecimal("1000.00"), new BigDecimal("10.00"),
                new BigDecimal("100.00"), new BigDecimal("900.00"), true));
        when(tierPerkRepository.findAllByTierIdOrderByPerkNameAsc(TIER_ID))
            .thenReturn(List.of());

        OrderBenefitService.BenefitResult result = orderBenefitService.evaluate(
            USER_ID, new BigDecimal("1000.00"), "GROCERY");

        assertThat(result.deliveryFee()).isEqualByComparingTo("50.00");
        assertThat(result.finalAmount()).isEqualByComparingTo("950.00");
        assertThat(result.freeDelivery()).isFalse();
    }

    /**
     * Creates an active Gold subscription fixture.
     *
     * @return initialized subscription fixture
     * @implNote Used internally by subscribed-user benefit tests.
     */
    private Subscription subscription() {
        User user = initialize(
            new User("member@example.com", "Member", "User", null), USER_ID);
        Tier tier = initialize(new Tier(
            "GOLD", "Gold", "Gold tier", 2,
            new BigDecimal("299.00"), new BigDecimal("799.00"),
            new BigDecimal("2999.00")), TIER_ID);
        return initialize(new Subscription(
            user, tier, BillingCycle.MONTHLY,
            Instant.parse("2026-06-21T06:30:00Z"),
            new BigDecimal("299.00"), "INR"), UUID.randomUUID());
    }
}
