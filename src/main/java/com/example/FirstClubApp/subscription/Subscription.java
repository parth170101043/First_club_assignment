package com.example.FirstClubApp.subscription;

import com.example.FirstClubApp.common.AuditableEntity;
import com.example.FirstClubApp.tier.Tier;
import com.example.FirstClubApp.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores the explicit plan, paid minimum tier, effective tier, and renewal lifecycle.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "min_tier_id", nullable = false)
    private Tier minTier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_tier_id", nullable = false)
    private Tier currentTier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "computed_behavioral_tier_id")
    private Tier computedBehavioralTier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_min_tier_id")
    private Tier scheduledMinTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "price_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePaid;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "renewal_payment_method_id")
    private UUID renewalPaymentMethodId;

    /**
     * Required by JPA when materializing a subscription from the database.
     *
     * @return a subscription with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected Subscription() {
    }

    /**
     * Creates an active subscription and calculates its expiry.
     *
     * @param user subscribing member
     * @param billingCycle purchased subscription duration
     * @param minTier explicitly purchased minimum tier
     * @param startsAt activation instant
     * @param pricePaid immutable price snapshot charged for the period
     * @param currency three-letter currency code; defaults to {@code INR} in the service
     * @param renewalPaymentMethodId payment method used for future renewals
     * @return a new active subscription
     * @implNote Used by {@link SubscriptionService} after user and tier validation.
     */
    public Subscription(User user, BillingCycle billingCycle, Tier minTier, Instant startsAt,
                        BigDecimal pricePaid, String currency,
                        UUID renewalPaymentMethodId) {
        this.user = user;
        this.billingCycle = billingCycle;
        this.minTier = minTier;
        this.currentTier = minTier;
        this.status = SubscriptionStatus.ACTIVE;
        this.startsAt = startsAt;
        this.expiresAt = billingCycle.expiryFrom(startsAt);
        this.pricePaid = pricePaid;
        this.currency = currency;
        this.renewalPaymentMethodId = renewalPaymentMethodId;
    }

    /**
     * Creates an active subscription without a stored renewal method.
     *
     * @param user subscribing member
     * @param billingCycle purchased subscription duration
     * @param minTier explicitly purchased minimum tier
     * @param startsAt activation instant
     * @param pricePaid immutable price snapshot charged for the period
     * @param currency three-letter currency code
     * @return a new active subscription with no automatic renewal method
     * @implNote Retained for existing tests and legacy callers.
     */
    public Subscription(User user, BillingCycle billingCycle, Tier minTier, Instant startsAt,
                        BigDecimal pricePaid, String currency) {
        this(user, billingCycle, minTier, startsAt, pricePaid, currency, null);
    }

    /**
     * Creates an active subscription using the original parameter order.
     *
     * @param user subscribing member
     * @param tier paid and effective tier
     * @param billingCycle selected duration enum
     * @param startsAt activation instant
     * @param pricePaid price snapshot
     * @param currency three-letter currency code
     * @return a new active subscription
     * @implNote Retained for existing callers.
     */
    public Subscription(User user, Tier tier, BillingCycle billingCycle, Instant startsAt,
                        BigDecimal pricePaid, String currency) {
        this(user, billingCycle, tier, startsAt, pricePaid, currency);
    }

    /**
     * Immediately raises the paid minimum tier after a successful upgrade payment.
     *
     * @param newMinTier higher paid tier
     * @param newFullPrice full configured plan price for the new paid tier
     * @return no return value
     * @implNote Used by {@link SubscriptionService} after charging the price difference.
     */
    public void upgradeMinTier(Tier newMinTier, BigDecimal newFullPrice) {
        this.minTier = newMinTier;
        this.pricePaid = newFullPrice;
        this.scheduledMinTier = null;
        recalculateCurrentTier();
    }

    /**
     * Replaces the payment method used by future subscription renewals.
     *
     * @param paymentMethodId active user-owned payment method UUID
     * @return no return value
     * @implNote Used after a successful purchase or paid upgrade.
     */
    public void changeRenewalPaymentMethod(UUID paymentMethodId) {
        this.renewalPaymentMethodId = paymentMethodId;
    }

    /**
     * Schedules a lower paid minimum tier for the next renewal.
     *
     * @param newMinTier lower tier selected by the user
     * @return no return value
     * @implNote Used by {@link SubscriptionService} for downgrade requests.
     */
    public void scheduleMinTierDowngrade(Tier newMinTier) {
        this.scheduledMinTier = newMinTier;
    }

    /**
     * Applies behavioral evaluation and recalculates the effective benefit tier.
     *
     * @param behavioralTier highest tier earned from behavioral strategies; may be {@code null}
     * @return no return value
     * @implNote Used by tier evaluation without changing the paid minimum tier.
     */
    public void applyBehavioralTier(Tier behavioralTier) {
        if (sameTier(this.computedBehavioralTier, behavioralTier)) {
            return;
        }
        this.computedBehavioralTier = behavioralTier;
        recalculateCurrentTier();
    }

    /**
     * Checks whether two optional tier references represent the same tier.
     *
     * @param first first tier reference; defaults to {@code null}
     * @param second second tier reference; defaults to {@code null}
     * @return {@code true} when both are absent or have the same identifier
     * @implNote Used internally to keep repeated behavioral reevaluation idempotent.
     */
    private boolean sameTier(Tier first, Tier second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.getId().equals(second.getId());
    }

    /**
     * Renews the subscription and applies a scheduled paid-tier downgrade.
     *
     * @param renewalStart start of the renewed billing period
     * @param renewedPrice configured price for the renewed plan and paid tier
     * @return no return value
     * @implNote Used by renewal processing for due active subscriptions.
     */
    public void renew(Instant renewalStart, BigDecimal renewedPrice) {
        if (scheduledMinTier != null) {
            minTier = scheduledMinTier;
            scheduledMinTier = null;
        }
        startsAt = renewalStart;
        expiresAt = billingCycle.expiryFrom(renewalStart);
        pricePaid = renewedPrice;
        cancelledAt = null;
        cancelAtPeriodEnd = false;
        recalculateCurrentTier();
    }

    /**
     * Recalculates the effective tier as the higher of paid and behavioral tiers.
     *
     * @return no return value
     * @implNote Used internally after paid-tier, behavioral-tier, or renewal changes.
     */
    private void recalculateCurrentTier() {
        if (computedBehavioralTier != null
            && computedBehavioralTier.getRank() > minTier.getRank()) {
            currentTier = computedBehavioralTier;
        } else {
            currentTier = minTier;
        }
    }

    /**
     * Schedules cancellation at the current period's expiry without issuing a refund.
     *
     * @param cancelledAt instant at which cancellation was requested
     * @return no return value
     * @implNote Used by {@link SubscriptionService#cancel(java.util.UUID)}.
     */
    public void scheduleCancellation(Instant cancelledAt) {
        this.cancelAtPeriodEnd = true;
        this.cancelledAt = cancelledAt;
    }

    /**
     * Removes a pending period-end cancellation.
     *
     * @return no return value
     * @implNote Used by {@link SubscriptionService#reactivate(java.util.UUID)}.
     */
    public void reactivate() {
        this.cancelAtPeriodEnd = false;
        this.cancelledAt = null;
    }

    /**
     * Closes a due subscription according to its cancellation flag.
     *
     * @return no return value
     * @implNote Used by the scheduled and manually triggered expiry processing service.
     */
    public void expire() {
        this.status = cancelAtPeriodEnd ? SubscriptionStatus.CANCELLED : SubscriptionStatus.EXPIRED;
    }

    /**
     * Returns the subscribing user.
     *
     * @return associated user
     * @implNote Used by locking logic and subscription response mapping.
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns the effective benefit tier.
     *
     * @return associated tier
     * @implNote Used by subscription response mapping.
     */
    public Tier getTier() {
        return currentTier;
    }

    /**
     * Returns the selected billing cycle.
     *
     * @return Monthly, Quarterly, or Yearly
     * @implNote Used by subscription response mapping.
     */
    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    /**
     * Returns the explicitly paid minimum tier.
     *
     * @return paid minimum tier
     * @implNote Used by pricing, upgrade, downgrade, and subscription responses.
     */
    public Tier getMinTier() {
        return minTier;
    }

    /**
     * Returns the effective tier controlling benefits.
     *
     * @return maximum of paid and behavioral tiers
     * @implNote Used by perks, checkout, homepage, and subscription responses.
     */
    public Tier getCurrentTier() {
        return currentTier;
    }

    /**
     * Returns the highest tier earned from behavioral evaluation.
     *
     * @return behavioral tier, or {@code null} when none is earned
     * @implNote Used by subscription responses and tier evaluation.
     */
    public Tier getComputedBehavioralTier() {
        return computedBehavioralTier;
    }

    /**
     * Returns the paid tier scheduled for the next renewal.
     *
     * @return scheduled downgrade tier, or {@code null}
     * @implNote Used by subscription responses and renewal processing.
     */
    public Tier getScheduledMinTier() {
        return scheduledMinTier;
    }

    /**
     * Returns the current subscription status.
     *
     * @return lifecycle status
     * @implNote Used by persistence queries and subscription responses.
     */
    public SubscriptionStatus getStatus() {
        return status;
    }

    /**
     * Returns the activation instant.
     *
     * @return subscription start instant
     * @implNote Used by subscription response mapping.
     */
    public Instant getStartsAt() {
        return startsAt;
    }

    /**
     * Returns the period expiry instant.
     *
     * @return subscription expiry instant
     * @implNote Used by expiry processing and subscription responses.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Reports whether cancellation is scheduled for period end.
     *
     * @return {@code false} by default for a new subscription
     * @implNote Used by expiry processing and subscription responses.
     */
    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    /**
     * Returns when cancellation was requested.
     *
     * @return cancellation request instant, or {@code null} when not scheduled
     * @implNote Used by subscription response mapping.
     */
    public Instant getCancelledAt() {
        return cancelledAt;
    }

    /**
     * Returns the immutable price snapshot paid for the period.
     *
     * @return charged price
     * @implNote Used by subscription response mapping and future billing records.
     */
    public BigDecimal getPricePaid() {
        return pricePaid;
    }

    /**
     * Returns the payment currency.
     *
     * @return three-letter currency code
     * @implNote Used by subscription response mapping.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Returns the payment method selected for recurring renewal.
     *
     * @return payment method UUID, or {@code null} for legacy subscriptions
     * @implNote Used by renewal processing and subscription responses.
     */
    public UUID getRenewalPaymentMethodId() {
        return renewalPaymentMethodId;
    }
}
