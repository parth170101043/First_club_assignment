package com.example.FirstClubApp.subscription;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.payment.PaymentDtos;
import com.example.FirstClubApp.payment.PaymentService;
import com.example.FirstClubApp.payment.PaymentStatus;
import com.example.FirstClubApp.plan.MembershipPlanService;
import com.example.FirstClubApp.plan.PlanTierPrice;
import com.example.FirstClubApp.tier.Tier;
import com.example.FirstClubApp.tier.TierService;
import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates explicit plan subscriptions, paid tier changes, effective tiers, and renewals.
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserService userService;
    private final TierService tierService;
    private final MembershipPlanService planService;
    private final PaymentService paymentService;
    private final Clock clock;

    /**
     * Creates the subscription lifecycle service.
     *
     * @param subscriptionRepository persistence and locking gateway
     * @param userService user validation service
     * @param tierService tier validation service
     * @param planService plan and pricing service
     * @param paymentService mock payment service used for purchases, upgrades, and renewals
     * @param clock UTC time source
     * @return an initialized service
     * @implNote Used by Spring dependency injection and replaceable in tests.
     */
    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               UserService userService,
                               TierService tierService,
                               MembershipPlanService planService,
                               PaymentService paymentService,
                               Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.userService = userService;
        this.tierService = tierService;
        this.planService = planService;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    /**
     * Creates a compatibility service for legacy unit tests without plan or payment mocks.
     *
     * @param subscriptionRepository persistence and locking gateway
     * @param userService user validation service
     * @param tierService tier validation service
     * @param clock UTC time source
     * @return a compatibility service using legacy tier price fields
     * @implNote Production dependency injection uses the complete constructor.
     */
    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               UserService userService,
                               TierService tierService,
                               Clock clock) {
        this(subscriptionRepository, userService, tierService, null, null, clock);
    }

    /**
     * Creates a subscription from an explicit plan and paid minimum tier.
     *
     * @param request validated user, plan, and tier selection
     * @return newly created active subscription
     * @implNote Used by {@link SubscriptionController#subscribe(SubscriptionDtos.CreateRequest)}.
     */
    @Transactional
    public SubscriptionDtos.Response subscribe(SubscriptionDtos.CreateRequest request) {
        processDueSubscriptions();
        if (subscriptionRepository.findActiveForUpdate(request.userId()).isPresent()) {
            throw new ConflictException("The user already has an active subscription.");
        }
        User user = userService.requireUser(request.userId());
        if (!user.isEnabled()) {
            throw new ConflictException("The user account is disabled.");
        }
        Tier minTier = tierService.requireActiveTier(request.tierId());
        BigDecimal price;
        String currency;
        if (planService == null) {
            price = minTier.priceFor(request.billingCycle());
            currency = "INR";
        } else {
            PlanTierPrice pricing = planService.requirePrice(
                request.billingCycle(), minTier.getId());
            price = pricing.getPrice();
            currency = pricing.getCurrency();
            requireSuccessfulPayment(
                request.userId(),
                request.paymentMethodId(),
                price,
                currency,
                "Membership subscription: "
                    + request.billingCycle() + " " + minTier.getCode()
            );
        }
        Subscription subscription = new Subscription(
            user, request.billingCycle(), minTier, clock.instant(),
            price, currency, request.paymentMethodId());
        return SubscriptionDtos.Response.from(subscriptionRepository.save(subscription));
    }

    /**
     * Immediately upgrades the paid minimum tier after charging the prorated price difference.
     *
     * @param subscriptionId active subscription UUID
     * @param request higher tier and payment method
     * @return upgraded subscription response
     * @implNote Used by the paid upgrade API.
     */
    @Transactional
    public SubscriptionDtos.Response upgrade(
        UUID subscriptionId,
        SubscriptionDtos.UpgradeRequest request) {
        Subscription subscription = requireActiveForUpdate(subscriptionId);
        Tier newTier = tierService.requireActiveTier(request.newTierId());
        if (newTier.getRank() <= subscription.getMinTier().getRank()) {
            throw new ConflictException("Upgrade tier must be higher than the paid minimum tier.");
        }
        PlanTierPrice newPricing = planService.requirePrice(
            subscription.getBillingCycle(), newTier.getId());
        BigDecimal fullPeriodDifference = newPricing.getPrice()
            .subtract(subscription.getPricePaid())
            .max(BigDecimal.ZERO);
        BigDecimal chargeAmount = prorateForRemainingPeriod(
            fullPeriodDifference,
            subscription.getStartsAt(),
            subscription.getExpiresAt(),
            clock.instant()
        );
        if (chargeAmount.signum() > 0) {
            requireSuccessfulPayment(
                subscription.getUser().getId(),
                request.paymentMethodId(),
                chargeAmount,
                newPricing.getCurrency(),
                "Membership tier upgrade to " + newTier.getCode()
            );
        }
        subscription.upgradeMinTier(newTier, newPricing.getPrice());
        subscription.changeRenewalPaymentMethod(request.paymentMethodId());
        return SubscriptionDtos.Response.from(subscription);
    }

    /**
     * Calculates the charge for upgrading now without changing the subscription.
     *
     * @param userId active member
     * @param newTierId requested higher paid tier
     * @return quote covering only the time remaining until the current expiry
     */
    @Transactional(readOnly = true)
    public SubscriptionDtos.UpgradeQuote quoteUpgrade(UUID userId, UUID newTierId) {
        Subscription subscription = requireActiveSubscription(userId);
        Tier newTier = tierService.requireActiveTier(newTierId);
        if (newTier.getRank() <= subscription.getMinTier().getRank()) {
            throw new ConflictException(
                "Choose a tier higher than your current paid membership.");
        }
        PlanTierPrice newPricing = planService.requirePrice(
            subscription.getBillingCycle(), newTier.getId());
        BigDecimal chargeAmount = prorateForRemainingPeriod(
            newPricing.getPrice().subtract(subscription.getPricePaid()).max(BigDecimal.ZERO),
            subscription.getStartsAt(),
            subscription.getExpiresAt(),
            clock.instant()
        );
        return new SubscriptionDtos.UpgradeQuote(
            subscription.getId(),
            newTier.getId(),
            newTier.getCode(),
            newTier.getName(),
            subscription.getBillingCycle(),
            subscription.getPricePaid(),
            newPricing.getPrice(),
            chargeAmount,
            newPricing.getCurrency(),
            subscription.getExpiresAt()
        );
    }

    /**
     * Prorates an upgrade difference from the upgrade instant through the existing expiry.
     *
     * @param fullPeriodDifference configured full-period difference between paid tiers
     * @param startsAt start of the current subscription period
     * @param expiresAt unchanged end of the current subscription period
     * @param upgradedAt instant at which the paid upgrade is requested
     * @return remaining-period charge rounded to currency precision
     * @implNote Manual upgrades keep the original expiry and pay only for unused term time.
     */
    private BigDecimal prorateForRemainingPeriod(
        BigDecimal fullPeriodDifference,
        Instant startsAt,
        Instant expiresAt,
        Instant upgradedAt
    ) {
        if (fullPeriodDifference.signum() <= 0 || !upgradedAt.isBefore(expiresAt)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Instant effectiveUpgradeAt = upgradedAt.isBefore(startsAt) ? startsAt : upgradedAt;
        long totalSeconds = Duration.between(startsAt, expiresAt).getSeconds();
        long remainingSeconds = Duration.between(effectiveUpgradeAt, expiresAt).getSeconds();
        if (totalSeconds <= 0 || remainingSeconds <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return fullPeriodDifference
            .multiply(BigDecimal.valueOf(remainingSeconds))
            .divide(BigDecimal.valueOf(totalSeconds), 2, RoundingMode.HALF_UP);
    }

    /**
     * Schedules a lower paid minimum tier for the next renewal.
     *
     * @param subscriptionId active subscription UUID
     * @param request lower tier selection
     * @return subscription response containing the scheduled tier
     * @implNote Used by the downgrade API without removing paid benefits mid-cycle.
     */
    @Transactional
    public SubscriptionDtos.Response downgrade(
        UUID subscriptionId,
        SubscriptionDtos.DowngradeRequest request) {
        Subscription subscription = requireActiveForUpdate(subscriptionId);
        Tier newTier = tierService.requireActiveTier(request.newTierId());
        if (newTier.getRank() >= subscription.getMinTier().getRank()) {
            throw new ConflictException(
                "Downgrade tier must be lower than the paid minimum tier.");
        }
        planService.requirePrice(subscription.getBillingCycle(), newTier.getId());
        subscription.scheduleMinTierDowngrade(newTier);
        return SubscriptionDtos.Response.from(subscription);
    }

    /**
     * Returns the user's current active subscription.
     *
     * @param userId user UUID
     * @return current subscription response
     * @implNote Used by membership clients and the homepage.
     */
    @Transactional
    public SubscriptionDtos.Response currentForUser(UUID userId) {
        return SubscriptionDtos.Response.from(requireActiveSubscription(userId));
    }

    /**
     * Returns the current subscription when one exists.
     *
     * @param userId member being checked
     * @return optional response safe for browser-page rendering
     */
    @Transactional
    public Optional<SubscriptionDtos.Response> findCurrentForUser(UUID userId) {
        processDueSubscriptions();
        return subscriptionRepository.findByUserIdAndStatus(
                userId, SubscriptionStatus.ACTIVE)
            .map(SubscriptionDtos.Response::from);
    }

    /**
     * Requires an active subscription after processing due renewals.
     *
     * @param userId user UUID
     * @return active subscription entity
     * @implNote Used by perks, checkout, homepage, and tier evaluation.
     */
    @Transactional
    public Subscription requireActiveSubscription(UUID userId) {
        return findActiveSubscription(userId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No active subscription found for user: " + userId));
    }

    /**
     * Finds an active subscription after processing due renewals.
     *
     * @param userId user UUID
     * @return active subscription, or an empty optional
     * @implNote Used when non-membership is a valid outcome.
     */
    @Transactional
    public Optional<Subscription> findActiveSubscription(UUID userId) {
        processDueSubscriptions();
        return subscriptionRepository.findByUserIdAndStatus(
            userId, SubscriptionStatus.ACTIVE);
    }

    /**
     * Returns complete subscription history.
     *
     * @param userId user UUID
     * @return subscriptions ordered newest first
     * @implNote Used by membership history clients.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionDtos.Response> historyForUser(UUID userId) {
        userService.requireUser(userId);
        return subscriptionRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(SubscriptionDtos.Response::from)
            .toList();
    }

    /**
     * Lists every subscription for administrative management.
     *
     * @return all subscriptions ordered by repository persistence order
     */
    @Transactional(readOnly = true)
    public List<SubscriptionDtos.Response> findAllForAdmin() {
        return subscriptionRepository.findAll().stream()
            .map(SubscriptionDtos.Response::from)
            .toList();
    }

    /**
     * Creates a manual administrator-issued subscription without charging a payment method.
     *
     * @param userId receiving member
     * @param tierId paid minimum tier
     * @param billingCycle selected duration
     * @return created active subscription
     */
    @Transactional
    public SubscriptionDtos.Response createForAdmin(
        UUID userId, UUID tierId, BillingCycle billingCycle) {
        processDueSubscriptions();
        if (subscriptionRepository.findActiveForUpdate(userId).isPresent()) {
            throw new ConflictException("The user already has an active subscription.");
        }
        User user = userService.requireUser(userId);
        Tier tier = tierService.requireActiveTier(tierId);
        PlanTierPrice pricing = planService.requirePrice(billingCycle, tierId);
        Subscription subscription = new Subscription(
            user, billingCycle, tier, clock.instant(),
            pricing.getPrice(), pricing.getCurrency(), null);
        return SubscriptionDtos.Response.from(subscriptionRepository.save(subscription));
    }

    /**
     * Ends an active subscription immediately while preserving its history.
     *
     * @param subscriptionId active subscription UUID
     * @return ended subscription
     */
    @Transactional
    public SubscriptionDtos.Response endForAdmin(UUID subscriptionId) {
        Subscription subscription = requireActiveForUpdate(subscriptionId);
        subscription.expire();
        return SubscriptionDtos.Response.from(subscription);
    }

    /**
     * Schedules cancellation at period end.
     *
     * @param subscriptionId active subscription UUID
     * @return updated subscription response
     * @implNote Used by cancellation clients.
     */
    @Transactional
    public SubscriptionDtos.Response cancel(UUID subscriptionId) {
        Subscription subscription = requireActiveForUpdate(subscriptionId);
        if (!subscription.isCancelAtPeriodEnd()) {
            subscription.scheduleCancellation(clock.instant());
        }
        return SubscriptionDtos.Response.from(subscription);
    }

    /**
     * Reverses a scheduled cancellation before renewal.
     *
     * @param subscriptionId active subscription UUID
     * @return reactivated subscription response
     * @implNote Used by membership clients.
     */
    @Transactional
    public SubscriptionDtos.Response reactivate(UUID subscriptionId) {
        Subscription subscription = requireActiveForUpdate(subscriptionId);
        if (!subscription.isCancelAtPeriodEnd()) {
            throw new ConflictException("The subscription is not scheduled for cancellation.");
        }
        subscription.reactivate();
        return SubscriptionDtos.Response.from(subscription);
    }

    /**
     * Processes due subscriptions by cancelling or renewing them.
     *
     * @return number of subscriptions processed
     * @implNote Used by the scheduler and manual lifecycle endpoint.
     */
    @Transactional
    public int processDueSubscriptions() {
        Instant now = clock.instant();
        List<Subscription> due = subscriptionRepository
            .findAllByStatusAndExpiresAtLessThanEqual(SubscriptionStatus.ACTIVE, now);
        for (Subscription subscription : due) {
            if (subscription.isCancelAtPeriodEnd()) {
                subscription.expire();
            } else if (planService == null) {
                subscription.expire();
            } else {
                Tier renewalTier = subscription.getScheduledMinTier() == null
                    ? subscription.getMinTier()
                    : subscription.getScheduledMinTier();
                PlanTierPrice pricing = planService.requirePrice(
                    subscription.getBillingCycle(), renewalTier.getId());
                if (subscription.getRenewalPaymentMethodId() == null
                    || !paymentSucceeded(
                        subscription.getUser().getId(),
                        subscription.getRenewalPaymentMethodId(),
                        pricing.getPrice(),
                        pricing.getCurrency(),
                        "Membership renewal: "
                            + subscription.getBillingCycle() + " "
                            + renewalTier.getCode())) {
                    subscription.expire();
                } else {
                    subscription.renew(subscription.getExpiresAt(), pricing.getPrice());
                }
            }
        }
        return due.size();
    }

    /**
     * Retains the previous expiry endpoint method name for API compatibility.
     *
     * @return number of due subscriptions cancelled or renewed
     * @implNote Used by existing scheduler and controller code.
     */
    @Transactional
    public int expireDueSubscriptions() {
        return processDueSubscriptions();
    }

    /**
     * Loads and locks the requested active subscription.
     *
     * @param subscriptionId subscription UUID
     * @return locked active subscription
     * @implNote Used internally by lifecycle mutations.
     */
    private Subscription requireActiveForUpdate(UUID subscriptionId) {
        Subscription found = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Subscription not found: " + subscriptionId));
        return subscriptionRepository.findActiveForUpdate(found.getUser().getId())
            .filter(value -> value.getId().equals(subscriptionId))
            .orElseThrow(() -> new ConflictException("The subscription is not active."));
    }

    /**
     * Charges a subscription action and raises a conflict when payment fails.
     *
     * @param userId payment owner UUID
     * @param paymentMethodId active user-owned payment method UUID
     * @param amount non-negative amount to charge
     * @param currency three-letter currency code
     * @param purpose human-readable subscription action
     * @return no return value
     * @implNote Used by initial purchase and immediate paid upgrade flows.
     */
    private void requireSuccessfulPayment(
        UUID userId,
        UUID paymentMethodId,
        BigDecimal amount,
        String currency,
        String purpose
    ) {
        PaymentDtos.TransactionResponse payment = paymentService.charge(
            new PaymentDtos.ChargeRequest(
                userId, paymentMethodId, amount, currency, purpose));
        if (payment.status() != PaymentStatus.SUCCEEDED) {
            throw new ConflictException(
                "Subscription payment failed: " + payment.failureReason());
        }
    }

    /**
     * Attempts a recurring charge without aborting the entire renewal batch.
     *
     * @param userId payment owner UUID
     * @param paymentMethodId stored renewal payment method UUID
     * @param amount configured renewal amount
     * @param currency three-letter currency code
     * @param purpose human-readable renewal description
     * @return {@code true} when the mock processor succeeds
     * @implNote Used by due-subscription processing; failure removes active membership.
     */
    private boolean paymentSucceeded(
        UUID userId,
        UUID paymentMethodId,
        BigDecimal amount,
        String currency,
        String purpose
    ) {
        try {
            PaymentDtos.TransactionResponse payment = paymentService.charge(
                new PaymentDtos.ChargeRequest(
                    userId, paymentMethodId, amount, currency, purpose));
            return payment.status() == PaymentStatus.SUCCEEDED;
        } catch (RuntimeException exception) {
            return false;
        }
    }

}
