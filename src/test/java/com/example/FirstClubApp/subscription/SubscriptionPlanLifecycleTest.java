package com.example.FirstClubApp.subscription;

import com.example.FirstClubApp.payment.PaymentDtos;
import com.example.FirstClubApp.payment.PaymentService;
import com.example.FirstClubApp.payment.PaymentStatus;
import com.example.FirstClubApp.plan.MembershipPlanService;
import com.example.FirstClubApp.plan.PlanTierPrice;
import com.example.FirstClubApp.tier.Tier;
import com.example.FirstClubApp.tier.TierService;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies explicit plans, paid upgrades, scheduled downgrades, renewal, and effective tier floors.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionPlanLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-06-21T06:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_METHOD_ID = UUID.randomUUID();

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserService userService;

    @Mock
    private TierService tierService;

    @Mock
    private MembershipPlanService planService;

    @Mock
    private PaymentService paymentService;

    private SubscriptionService subscriptionService;

    /**
     * Creates the complete subscription service with fixed time and mocked dependencies.
     *
     * @return no return value
     * @implNote Used by JUnit before each explicit plan lifecycle test.
     */
    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
            subscriptionRepository,
            userService,
            tierService,
            planService,
            paymentService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    /**
     * Verifies subscription creation uses an explicit plan and plan-tier price.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect plan plus tier subscription selection.
     */
    @Test
    void subscribesToExplicitPlanAndTier() {
        UUID tierId = UUID.randomUUID();
        User user = user();
        Tier gold = tier("GOLD", 2);
        initialize(gold, tierId);
        PlanTierPrice pricing = price(BillingCycle.QUARTERLY, gold, "250.00");
        when(subscriptionRepository
            .findAllByStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, NOW))
            .thenReturn(List.of());
        when(subscriptionRepository.findActiveForUpdate(USER_ID)).thenReturn(Optional.empty());
        when(userService.requireUser(USER_ID)).thenReturn(user);
        when(tierService.requireActiveTier(tierId)).thenReturn(gold);
        when(planService.requirePrice(BillingCycle.QUARTERLY, tierId))
            .thenReturn(pricing);
        when(paymentService.charge(any(PaymentDtos.ChargeRequest.class)))
            .thenReturn(successfulPayment("250.00"));
        when(subscriptionRepository.save(any(Subscription.class)))
            .thenAnswer(invocation ->
                initialize(invocation.getArgument(0), UUID.randomUUID()));

        SubscriptionDtos.Response response = subscriptionService.subscribe(
            new SubscriptionDtos.CreateRequest(
                USER_ID, tierId, BillingCycle.QUARTERLY, PAYMENT_METHOD_ID));

        assertThat(response.billingCycle()).isEqualTo(BillingCycle.QUARTERLY);
        assertThat(response.minTierCode()).isEqualTo("GOLD");
        assertThat(response.currentTierCode()).isEqualTo("GOLD");
        assertThat(response.pricePaid()).isEqualByComparingTo("250.00");
        assertThat(response.renewalPaymentMethodId())
            .isEqualTo(PAYMENT_METHOD_ID);
        assertThat(response.expiresAt()).isEqualTo(
            Instant.parse("2026-09-21T06:30:00Z"));
    }

    /**
     * Verifies a paid upgrade charges the price difference only for the remaining period.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect paid tier upgrades.
     */
    @Test
    void proratesDifferenceThroughExistingExpiryAndUpgradesImmediately() {
        UUID subscriptionId = UUID.randomUUID();
        UUID methodId = UUID.randomUUID();
        Tier silver = tier("SILVER", 1);
        Tier gold = tier("GOLD", 2);
        Subscription subscription = initialize(new Subscription(
            user(), BillingCycle.YEARLY, silver,
            Instant.parse("2026-01-01T00:00:00Z"),
            new BigDecimal("100.00"), "INR", PAYMENT_METHOD_ID), subscriptionId);
        Instant originalExpiry = subscription.getExpiresAt();
        when(subscriptionRepository.findById(subscriptionId))
            .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findActiveForUpdate(USER_ID))
            .thenReturn(Optional.of(subscription));
        when(tierService.requireActiveTier(gold.getId())).thenReturn(gold);
        when(planService.requirePrice(BillingCycle.YEARLY, gold.getId()))
            .thenReturn(price(BillingCycle.YEARLY, gold, "150.00"));
        when(paymentService.charge(any(PaymentDtos.ChargeRequest.class)))
            .thenReturn(new PaymentDtos.TransactionResponse(
                UUID.randomUUID(), USER_ID, methodId, new BigDecimal("26.54"),
                "INR", "Upgrade", PaymentStatus.SUCCEEDED,
                "mock_pay_1", null, NOW));

        SubscriptionDtos.Response response = subscriptionService.upgrade(
            subscriptionId, new SubscriptionDtos.UpgradeRequest(gold.getId(), methodId));

        assertThat(response.minTierCode()).isEqualTo("GOLD");
        assertThat(response.currentTierCode()).isEqualTo("GOLD");
        assertThat(response.pricePaid()).isEqualByComparingTo("150.00");
        assertThat(response.expiresAt()).isEqualTo(originalExpiry);
        ArgumentCaptor<PaymentDtos.ChargeRequest> chargeCaptor =
            ArgumentCaptor.forClass(PaymentDtos.ChargeRequest.class);
        verify(paymentService).charge(chargeCaptor.capture());
        assertThat(chargeCaptor.getValue().amount()).isEqualByComparingTo("26.54");
    }

    /**
     * Verifies a downgrade remains scheduled and does not reduce current-period paid benefits.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect next-cycle downgrade semantics.
     */
    @Test
    void schedulesDowngradeWithoutImmediateTierLoss() {
        UUID subscriptionId = UUID.randomUUID();
        Tier gold = tier("GOLD", 2);
        Tier silver = tier("SILVER", 1);
        Subscription subscription = subscription(subscriptionId, gold, "250.00");
        when(subscriptionRepository.findById(subscriptionId))
            .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findActiveForUpdate(USER_ID))
            .thenReturn(Optional.of(subscription));
        when(tierService.requireActiveTier(silver.getId())).thenReturn(silver);
        when(planService.requirePrice(BillingCycle.QUARTERLY, silver.getId()))
            .thenReturn(price(BillingCycle.QUARTERLY, silver, "100.00"));

        SubscriptionDtos.Response response = subscriptionService.downgrade(
            subscriptionId, new SubscriptionDtos.DowngradeRequest(silver.getId()));

        assertThat(response.minTierCode()).isEqualTo("GOLD");
        assertThat(response.currentTierCode()).isEqualTo("GOLD");
        assertThat(response.scheduledMinTierCode()).isEqualTo("SILVER");
    }

    /**
     * Verifies renewal applies the scheduled tier and its configured plan price.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect downgrade application at renewal.
     */
    @Test
    void appliesScheduledDowngradeDuringRenewal() {
        Tier gold = tier("GOLD", 2);
        Tier silver = tier("SILVER", 1);
        Subscription subscription = subscription(UUID.randomUUID(), gold, "250.00");
        subscription.scheduleMinTierDowngrade(silver);
        when(subscriptionRepository.findAllByStatusAndExpiresAtLessThanEqual(
            SubscriptionStatus.ACTIVE, NOW)).thenReturn(List.of(subscription));
        when(planService.requirePrice(BillingCycle.QUARTERLY, silver.getId()))
            .thenReturn(price(BillingCycle.QUARTERLY, silver, "100.00"));
        when(paymentService.charge(any(PaymentDtos.ChargeRequest.class)))
            .thenReturn(successfulPayment("100.00"));

        int processed = subscriptionService.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getMinTier().getCode()).isEqualTo("SILVER");
        assertThat(subscription.getScheduledMinTier()).isNull();
        assertThat(subscription.getPricePaid()).isEqualByComparingTo("100.00");
    }

    /**
     * Verifies behavioral tiers may raise benefits but never lower them below the paid tier.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect minTier and currentTier separation.
     */
    @Test
    void keepsCurrentTierAtOrAbovePaidMinimum() {
        Tier gold = tier("GOLD", 2);
        Tier platinum = tier("PLATINUM", 3);
        Tier silver = tier("SILVER", 1);
        Subscription subscription = subscription(UUID.randomUUID(), gold, "250.00");

        subscription.applyBehavioralTier(platinum);
        assertThat(subscription.getCurrentTier().getCode()).isEqualTo("PLATINUM");

        subscription.applyBehavioralTier(silver);
        assertThat(subscription.getCurrentTier().getCode()).isEqualTo("GOLD");
        assertThat(subscription.getMinTier().getCode()).isEqualTo("GOLD");
    }

    /**
     * Verifies repeating the same behavioral result leaves tier state unchanged.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect idempotent reevaluation.
     */
    @Test
    void keepsRepeatedBehavioralEvaluationIdempotent() {
        Tier silver = tier("SILVER", 1);
        Tier platinum = tier("PLATINUM", 3);
        Subscription subscription = subscription(
            UUID.randomUUID(), silver, "100.00");

        subscription.applyBehavioralTier(platinum);
        Tier firstComputedTier = subscription.getComputedBehavioralTier();
        Tier firstCurrentTier = subscription.getCurrentTier();
        subscription.applyBehavioralTier(platinum);

        assertThat(subscription.getComputedBehavioralTier())
            .isSameAs(firstComputedTier);
        assertThat(subscription.getCurrentTier())
            .isSameAs(firstCurrentTier);
    }

    /**
     * Verifies a failed recurring payment ends membership instead of renewing benefits.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect payment-backed renewal.
     */
    @Test
    void expiresSubscriptionWhenRenewalPaymentFails() {
        Tier gold = tier("GOLD", 2);
        Subscription subscription = subscription(
            UUID.randomUUID(), gold, "250.00");
        when(subscriptionRepository.findAllByStatusAndExpiresAtLessThanEqual(
            SubscriptionStatus.ACTIVE, NOW)).thenReturn(List.of(subscription));
        when(planService.requirePrice(BillingCycle.QUARTERLY, gold.getId()))
            .thenReturn(price(BillingCycle.QUARTERLY, gold, "250.00"));
        when(paymentService.charge(any(PaymentDtos.ChargeRequest.class)))
            .thenReturn(new PaymentDtos.TransactionResponse(
                UUID.randomUUID(), USER_ID, PAYMENT_METHOD_ID,
                new BigDecimal("250.00"), "INR", "Renewal",
                PaymentStatus.FAILED, null, "Declined", NOW));

        subscriptionService.processDueSubscriptions();

        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.EXPIRED);
    }

    /**
     * Creates an initialized user fixture.
     *
     * @return enabled user
     * @implNote Used internally by explicit plan lifecycle tests.
     */
    private User user() {
        return initialize(
            new User("member@example.com", "Member", "User", null), USER_ID);
    }

    /**
     * Creates an initialized tier fixture.
     *
     * @param code tier code
     * @param rank tier rank
     * @return active tier
     * @implNote Used internally by tier lifecycle tests.
     */
    private Tier tier(String code, int rank) {
        return initialize(new Tier(
            code, code, code + " tier", rank,
            new BigDecimal("100.00"), new BigDecimal("250.00"),
            new BigDecimal("850.00")), UUID.randomUUID());
    }

    /**
     * Creates initialized plan-tier pricing.
     *
     * @param billingCycle duration enum
     * @param tier paid tier
     * @param amount configured price
     * @return active pricing fixture
     * @implNote Used internally by subscription lifecycle tests.
     */
    private PlanTierPrice price(
        BillingCycle billingCycle, Tier tier, String amount) {
        return initialize(
            new PlanTierPrice(
                billingCycle, tier, new BigDecimal(amount), "INR"),
            UUID.randomUUID());
    }

    /**
     * Creates an initialized active subscription fixture.
     *
     * @param subscriptionId subscription UUID
     * @param minTier paid minimum tier
     * @param amount price snapshot
     * @return active subscription
     * @implNote Used internally by upgrade, downgrade, and renewal tests.
     */
    private Subscription subscription(
        UUID subscriptionId, Tier minTier, String amount) {
        return initialize(new Subscription(
            user(), BillingCycle.QUARTERLY, minTier,
            Instant.parse("2026-03-21T06:30:00Z"),
            new BigDecimal(amount), "INR", PAYMENT_METHOD_ID), subscriptionId);
    }

    /**
     * Creates a successful mock payment response.
     *
     * @param amount charged amount
     * @return successful payment transaction
     * @implNote Used internally by purchase and renewal lifecycle tests.
     */
    private PaymentDtos.TransactionResponse successfulPayment(String amount) {
        return new PaymentDtos.TransactionResponse(
            UUID.randomUUID(), USER_ID, PAYMENT_METHOD_ID,
            new BigDecimal(amount), "INR", "Membership",
            PaymentStatus.SUCCEEDED, "mock_payment", null, NOW);
    }
}
