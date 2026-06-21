package com.example.FirstClubApp.subscription;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.tier.Tier;
import com.example.FirstClubApp.tier.TierService;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies subscription pricing, uniqueness, lifecycle mutations, history, and expiry processing.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-21T06:30:00Z");

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserService userService;

    @Mock
    private TierService tierService;

    private SubscriptionService subscriptionService;

    /**
     * Creates the service under test with a fixed UTC clock and mocked dependencies.
     *
     * @return no return value
     * @implNote Used by JUnit before each subscription service test.
     */
    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
            subscriptionRepository,
            userService,
            tierService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    /**
     * Verifies subscription creation uses the selected tier price and billing-cycle expiry.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect subscription purchase behavior.
     */
    @Test
    void createsSubscriptionWithPriceAndExpirySnapshot() {
        UUID userId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        User user = initialize(new User("member@example.com", "Member", "User", null), userId);
        Tier tier = initialize(tier(), tierId);
        when(subscriptionRepository.findAllByStatusAndExpiresAtLessThanEqual(
            SubscriptionStatus.ACTIVE, NOW)).thenReturn(List.of());
        when(subscriptionRepository.findActiveForUpdate(userId)).thenReturn(Optional.empty());
        when(userService.requireUser(userId)).thenReturn(user);
        when(tierService.requireActiveTier(tierId)).thenReturn(tier);
        when(subscriptionRepository.save(org.mockito.ArgumentMatchers.any(Subscription.class)))
            .thenAnswer(invocation -> initialize(invocation.getArgument(0), UUID.randomUUID()));

        SubscriptionDtos.Response response = subscriptionService.subscribe(
            new SubscriptionDtos.CreateRequest(userId, tierId, BillingCycle.QUARTERLY));

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.startsAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-09-21T06:30:00Z"));
        assertThat(response.pricePaid()).isEqualByComparingTo("799.00");
        assertThat(response.currency()).isEqualTo("INR");
    }

    /**
     * Verifies that a second active subscription for the same user is rejected.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect one-active-subscription concurrency rules.
     */
    @Test
    void rejectsDuplicateActiveSubscription() {
        UUID userId = UUID.randomUUID();
        Subscription existing = subscription(userId, UUID.randomUUID(), BillingCycle.MONTHLY);
        when(subscriptionRepository.findAllByStatusAndExpiresAtLessThanEqual(
            SubscriptionStatus.ACTIVE, NOW)).thenReturn(List.of());
        when(subscriptionRepository.findActiveForUpdate(userId))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> subscriptionService.subscribe(
            new SubscriptionDtos.CreateRequest(userId, UUID.randomUUID(), BillingCycle.MONTHLY)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already has");
        verify(subscriptionRepository, never())
            .save(org.mockito.ArgumentMatchers.any(Subscription.class));
    }

    /**
     * Verifies that cancellation remains active until period end and records request time.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect no-refund cancellation behavior.
     */
    @Test
    void schedulesCancellationAtPeriodEnd() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Subscription subscription = subscription(userId, subscriptionId, BillingCycle.MONTHLY);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findActiveForUpdate(userId))
            .thenReturn(Optional.of(subscription));

        SubscriptionDtos.Response response = subscriptionService.cancel(subscriptionId);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.cancelAtPeriodEnd()).isTrue();
        assertThat(response.cancelledAt()).isEqualTo(NOW);
    }

    /**
     * Verifies that a scheduled cancellation can be reversed before expiry.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect subscription reactivation behavior.
     */
    @Test
    void reactivatesScheduledCancellation() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Subscription subscription = subscription(userId, subscriptionId, BillingCycle.MONTHLY);
        subscription.scheduleCancellation(NOW.minusSeconds(60));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findActiveForUpdate(userId))
            .thenReturn(Optional.of(subscription));

        SubscriptionDtos.Response response = subscriptionService.reactivate(subscriptionId);

        assertThat(response.cancelAtPeriodEnd()).isFalse();
        assertThat(response.cancelledAt()).isNull();
    }

    /**
     * Verifies that reactivation is rejected when cancellation was not scheduled.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect invalid subscription transitions.
     */
    @Test
    void rejectsReactivationWithoutScheduledCancellation() {
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Subscription subscription = subscription(userId, subscriptionId, BillingCycle.MONTHLY);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.findActiveForUpdate(userId))
            .thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> subscriptionService.reactivate(subscriptionId))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("not scheduled");
    }

    /**
     * Verifies due subscriptions become expired or cancelled according to their cancellation flag.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect scheduled expiry processing.
     */
    @Test
    void expiresDueSubscriptions() {
        Subscription ordinary = subscription(UUID.randomUUID(), UUID.randomUUID(),
            BillingCycle.MONTHLY);
        Subscription cancelled = subscription(UUID.randomUUID(), UUID.randomUUID(),
            BillingCycle.MONTHLY);
        cancelled.scheduleCancellation(NOW.minusSeconds(60));
        when(subscriptionRepository.findAllByStatusAndExpiresAtLessThanEqual(
            SubscriptionStatus.ACTIVE, NOW)).thenReturn(List.of(ordinary, cancelled));

        int processed = subscriptionService.expireDueSubscriptions();

        assertThat(processed).isEqualTo(2);
        assertThat(ordinary.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    /**
     * Verifies a missing active subscription produces a domain not-found error.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect current-membership and user-perk behavior.
     */
    @Test
    void rejectsMissingActiveSubscription() {
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.findAllByStatusAndExpiresAtLessThanEqual(
            SubscriptionStatus.ACTIVE, NOW)).thenReturn(List.of());
        when(subscriptionRepository.findByUserIdAndStatus(
            userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.currentForUser(userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("No active subscription");
    }

    /**
     * Verifies current-subscription lookup returns the active membership response.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect membership home-screen lookup.
     */
    @Test
    void returnsCurrentSubscription() {
        UUID userId = UUID.randomUUID();
        Subscription subscription = subscription(userId, UUID.randomUUID(), BillingCycle.MONTHLY);
        when(subscriptionRepository.findAllByStatusAndExpiresAtLessThanEqual(
            SubscriptionStatus.ACTIVE, NOW)).thenReturn(List.of());
        when(subscriptionRepository.findByUserIdAndStatus(
            userId, SubscriptionStatus.ACTIVE)).thenReturn(Optional.of(subscription));

        SubscriptionDtos.Response response = subscriptionService.currentForUser(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    /**
     * Verifies subscription history preserves repository ordering in API responses.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect membership history retrieval.
     */
    @Test
    void returnsSubscriptionHistory() {
        UUID userId = UUID.randomUUID();
        Subscription newest = subscription(userId, UUID.randomUUID(), BillingCycle.YEARLY);
        Subscription oldest = subscription(userId, UUID.randomUUID(), BillingCycle.MONTHLY);
        when(subscriptionRepository.findAllByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(List.of(newest, oldest));

        List<SubscriptionDtos.Response> responses =
            subscriptionService.historyForUser(userId);

        verify(userService).requireUser(userId);
        assertThat(responses).extracting(SubscriptionDtos.Response::billingCycle)
            .containsExactly(BillingCycle.YEARLY, BillingCycle.MONTHLY);
    }

    /**
     * Creates an initialized subscription fixture.
     *
     * @param userId associated user UUID
     * @param subscriptionId subscription UUID
     * @param billingCycle billing cycle used to calculate expiry
     * @return active initialized subscription fixture
     * @implNote Used internally by subscription lifecycle tests.
     */
    private Subscription subscription(UUID userId, UUID subscriptionId,
                                      BillingCycle billingCycle) {
        User user = initialize(new User("member@example.com", "Member", "User", null), userId);
        Tier tier = initialize(tier(), UUID.randomUUID());
        Subscription subscription = new Subscription(user, tier, billingCycle, NOW,
            tier.priceFor(billingCycle), "INR");
        return initialize(subscription, subscriptionId);
    }

    /**
     * Creates an active Gold tier fixture with all billing prices.
     *
     * @return active tier fixture
     * @implNote Used internally by subscription creation and lifecycle tests.
     */
    private Tier tier() {
        return new Tier("GOLD", "Gold", "Gold tier", 2,
            new BigDecimal("299.00"), new BigDecimal("799.00"),
            new BigDecimal("2999.00"));
    }
}
