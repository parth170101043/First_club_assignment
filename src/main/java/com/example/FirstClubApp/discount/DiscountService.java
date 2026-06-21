package com.example.FirstClubApp.discount;

import com.example.FirstClubApp.perk.PerkType;
import com.example.FirstClubApp.perk.TierPerk;
import com.example.FirstClubApp.perk.TierPerkRepository;
import com.example.FirstClubApp.subscription.Subscription;
import com.example.FirstClubApp.subscription.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates active percentage-discount perks available through a current subscription.
 */
@Service
public class DiscountService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final TierPerkRepository tierPerkRepository;
    private final SubscriptionService subscriptionService;

    /**
     * Creates the perk-based discount evaluator.
     *
     * @param tierPerkRepository persistence gateway for current-tier perk assignments
     * @param subscriptionService active subscription lookup service
     * @return an initialized discount service
     * @implNote Discount creation and assignment are handled by the standard perk APIs.
     */
    public DiscountService(TierPerkRepository tierPerkRepository,
                           SubscriptionService subscriptionService) {
        this.tierPerkRepository = tierPerkRepository;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Evaluates all eligible discount perks from the user's active subscription.
     *
     * @param userId user UUID with no default value
     * @param request validated order amount
     * @return the single perk result that reduces the bill the most
     * @implNote Discount perks never stack.
     */
    @Transactional
    public DiscountDtos.EvaluationResponse evaluate(
        UUID userId,
        DiscountDtos.EvaluationRequest request) {
        Subscription subscription = subscriptionService.requireActiveSubscription(userId);
        return evaluate(subscription, request.orderAmount());
    }

    /**
     * Evaluates all active percentage-discount perks using an already resolved subscription.
     *
     * <p>A discount perk uses {@code discountPercent} and may optionally use
     * {@code maximumDiscount}. Invalid or non-positive discount configurations are ineligible.
     *
     * @param subscription active subscription that provides current-tier perks
     * @param orderAmount original order amount
     * @return best single discount and final payable amount
     * @implNote Each assignment uses the reusable perk's configuration.
     */
    @Transactional(readOnly = true)
    public DiscountDtos.EvaluationResponse evaluate(
        Subscription subscription,
        BigDecimal orderAmount) {
        DiscountCandidate best = tierPerkRepository
            .findAllByTierIdOrderByPerkNameAsc(subscription.getTier().getId())
            .stream()
            .filter(assignment -> assignment.getPerk().isActive())
            .filter(assignment ->
                assignment.getPerk().getType() == PerkType.PERCENTAGE_DISCOUNT)
            .map(assignment -> candidate(assignment, orderAmount))
            .filter(candidate -> candidate.discountAmount().signum() > 0)
            .max(Comparator.comparing(DiscountCandidate::discountAmount))
            .orElse(DiscountCandidate.none());

        BigDecimal finalAmount = orderAmount
            .subtract(best.discountAmount())
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);

        return new DiscountDtos.EvaluationResponse(
            subscription.getUser().getId(),
            subscription.getId(),
            subscription.getTier().getId(),
            orderAmount,
            best.discountPercent(),
            best.discountAmount(),
            finalAmount,
            best.discountAmount().signum() > 0,
            best.perkCode(),
            best.perkName()
        );
    }

    /**
     * Calculates one perk's monetary reduction.
     *
     * @param assignment active percentage-discount assignment
     * @param orderAmount original order amount
     * @return candidate result, or a zero candidate for invalid configuration
     * @implNote A maximum discount cap is optional and can only reduce the calculated benefit.
     */
    private DiscountCandidate candidate(TierPerk assignment, BigDecimal orderAmount) {
        Map<String, Object> configuration = assignment.getEffectiveConfiguration();
        BigDecimal percentage = decimal(configuration.get("discountPercent"));
        if (percentage == null
            || percentage.signum() <= 0
            || percentage.compareTo(ONE_HUNDRED) > 0) {
            return DiscountCandidate.none();
        }

        BigDecimal amount = orderAmount
            .multiply(percentage)
            .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal maximumDiscount = decimal(configuration.get("maximumDiscount"));
        if (maximumDiscount != null) {
            if (maximumDiscount.signum() < 0) {
                return DiscountCandidate.none();
            }
            amount = amount.min(maximumDiscount);
        }
        amount = amount.min(orderAmount).setScale(2, RoundingMode.HALF_UP);
        return new DiscountCandidate(
            percentage,
            amount,
            assignment.getPerk().getCode(),
            assignment.getPerk().getName()
        );
    }

    /**
     * Converts optional JSON-compatible numeric configuration to a decimal.
     *
     * @param value configuration value
     * @return decimal value, or {@code null} when absent or malformed
     * @implNote Malformed discount perks are ignored rather than blocking checkout.
     */
    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Stores one calculated percentage-perk result.
     */
    private record DiscountCandidate(
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        String perkCode,
        String perkName
    ) {

        /**
         * Creates the zero-discount fallback.
         *
         * @return zero candidate
         * @implNote Used when no active valid percentage perk reduces the bill.
         */
        private static DiscountCandidate none() {
            return new DiscountCandidate(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                null,
                null
            );
        }
    }
}
