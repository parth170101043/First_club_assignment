package com.example.FirstClubApp.subscription;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Groups billing-cycle, paid-tier, effective-tier, and lifecycle contracts.
 */
public final class SubscriptionDtos {

    /**
     * Prevents instantiation of this DTO namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime.
     */
    private SubscriptionDtos() {
    }

    /**
     * Defines the user, billing cycle, and paid minimum tier selected at purchase.
     */
    public record CreateRequest(
        @NotNull UUID userId,
        @NotNull UUID tierId,
        @NotNull BillingCycle billingCycle,
        @NotNull UUID paymentMethodId
    ) {
        /**
         * Creates a legacy request without payment details.
         *
         * @param userId subscribing user UUID
         * @param tierId selected paid tier UUID
         * @param billingCycle selected duration
         * @return request with no payment method
         * @implNote Retained for database-free legacy service tests.
         */
        public CreateRequest(
            UUID userId,
            UUID tierId,
            BillingCycle billingCycle
        ) {
            this(userId, tierId, billingCycle, null);
        }
    }

    /**
     * Defines an immediate paid tier upgrade.
     */
    public record UpgradeRequest(
        @NotNull UUID newTierId,
        @NotNull UUID paymentMethodId
    ) {
    }

    /**
     * Defines a paid-tier downgrade scheduled for renewal.
     */
    public record DowngradeRequest(
        @NotNull UUID newTierId
    ) {
    }

    /**
     * Describes the immediate prorated charge for a paid tier upgrade.
     */
    public record UpgradeQuote(
        UUID subscriptionId,
        UUID newTierId,
        String newTierCode,
        String newTierName,
        BillingCycle billingCycle,
        BigDecimal currentFullPrice,
        BigDecimal newFullPrice,
        BigDecimal chargeAmount,
        String currency,
        Instant expiresAt
    ) {
    }

    /**
     * Defines the complete billing-cycle and two-tier subscription representation.
     */
    public record Response(
        UUID id,
        UUID userId,
        String userEmail,
        BillingCycle billingCycle,
        String planName,
        UUID minTierId,
        String minTierCode,
        String minTierName,
        UUID currentTierId,
        String currentTierCode,
        String currentTierName,
        UUID computedBehavioralTierId,
        String computedBehavioralTierCode,
        UUID scheduledMinTierId,
        String scheduledMinTierCode,
        SubscriptionStatus status,
        Instant startsAt,
        Instant expiresAt,
        boolean cancelAtPeriodEnd,
        Instant cancelledAt,
        BigDecimal pricePaid,
        String currency,
        UUID renewalPaymentMethodId,
        long version
    ) {

        /**
         * Creates a response from the previous single-tier shape.
         *
         * @param id subscription UUID
         * @param userId user UUID
         * @param userEmail user email
         * @param tierId legacy tier UUID
         * @param tierCode legacy tier code
         * @param tierName legacy tier name
         * @param billingCycle selected duration
         * @param status lifecycle status
         * @param startsAt start instant
         * @param expiresAt expiry instant
         * @param cancelAtPeriodEnd cancellation flag
         * @param cancelledAt cancellation request instant
         * @param pricePaid price snapshot
         * @param currency currency code
         * @param version optimistic-lock version
         * @return response using the legacy tier as paid and effective tier
         * @implNote Retained for existing callers during migration.
         */
        public Response(
            UUID id, UUID userId, String userEmail,
            UUID tierId, String tierCode, String tierName,
            BillingCycle billingCycle, SubscriptionStatus status,
            Instant startsAt, Instant expiresAt,
            boolean cancelAtPeriodEnd, Instant cancelledAt,
            BigDecimal pricePaid, String currency, long version
        ) {
            this(
                id, userId, userEmail, billingCycle, planName(billingCycle),
                tierId, tierCode, tierName,
                tierId, tierCode, tierName,
                null, null, null, null,
                status, startsAt, expiresAt, cancelAtPeriodEnd, cancelledAt,
                pricePaid, currency, null, version
            );
        }

        /**
         * Maps a persistent subscription to its API response.
         *
         * @param subscription initialized subscription entity
         * @return immutable subscription response
         * @implNote Used by the subscription lifecycle service.
         */
        static Response from(Subscription subscription) {
            return new Response(
                subscription.getId(),
                subscription.getUser().getId(),
                subscription.getUser().getEmail(),
                subscription.getBillingCycle(),
                planName(subscription.getBillingCycle()),
                subscription.getMinTier().getId(),
                subscription.getMinTier().getCode(),
                subscription.getMinTier().getName(),
                subscription.getCurrentTier().getId(),
                subscription.getCurrentTier().getCode(),
                subscription.getCurrentTier().getName(),
                subscription.getComputedBehavioralTier() == null
                    ? null : subscription.getComputedBehavioralTier().getId(),
                subscription.getComputedBehavioralTier() == null
                    ? null : subscription.getComputedBehavioralTier().getCode(),
                subscription.getScheduledMinTier() == null
                    ? null : subscription.getScheduledMinTier().getId(),
                subscription.getScheduledMinTier() == null
                    ? null : subscription.getScheduledMinTier().getCode(),
                subscription.getStatus(),
                subscription.getStartsAt(),
                subscription.getExpiresAt(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCancelledAt(),
                subscription.getPricePaid(),
                subscription.getCurrency(),
                subscription.getRenewalPaymentMethodId(),
                subscription.getVersion()
            );
        }

        /**
         * Returns the effective tier UUID using the legacy accessor name.
         *
         * @return current tier UUID
         * @implNote Retained for source compatibility.
         */
        public UUID tierId() {
            return currentTierId;
        }

        /**
         * Returns the effective tier code using the legacy accessor name.
         *
         * @return current tier code
         * @implNote Retained for source compatibility.
         */
        public String tierCode() {
            return currentTierCode;
        }

        /**
         * Returns the effective tier name using the legacy accessor name.
         *
         * @return current tier name
         * @implNote Retained for source compatibility.
         */
        public String tierName() {
            return currentTierName;
        }

        /**
         * Returns a display label for a billing cycle.
         *
         * @param cycle supported billing cycle
         * @return user-facing duration name
         * @implNote Used by subscription response mapping.
         */
        private static String planName(BillingCycle cycle) {
            return switch (cycle) {
                case MONTHLY -> "Monthly";
                case QUARTERLY -> "Quarterly";
                case YEARLY -> "Yearly";
            };
        }
    }
}
