package com.example.FirstClubApp.order;

import com.example.FirstClubApp.discount.DiscountDtos;
import com.example.FirstClubApp.discount.DiscountService;
import com.example.FirstClubApp.perk.PerkType;
import com.example.FirstClubApp.perk.TierPerkRepository;
import com.example.FirstClubApp.subscription.Subscription;
import com.example.FirstClubApp.subscription.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Resolves discount and delivery benefits for an order using the user's active subscription.
 */
@Service
public class OrderBenefitService {

    public static final BigDecimal STANDARD_DELIVERY_FEE = new BigDecimal("50.00");

    private final SubscriptionService subscriptionService;
    private final DiscountService discountService;
    private final TierPerkRepository tierPerkRepository;

    /**
     * Creates the checkout benefit service.
     *
     * @param subscriptionService service that resolves optional active membership
     * @param discountService service that selects the best current-subscription discount perk
     * @param tierPerkRepository persistence gateway used to detect free-delivery assignments
     * @return an initialized benefit service
     * @implNote Used by {@link OrderService} before persisting an order.
     */
    public OrderBenefitService(SubscriptionService subscriptionService,
                               DiscountService discountService,
                               TierPerkRepository tierPerkRepository) {
        this.subscriptionService = subscriptionService;
        this.discountService = discountService;
        this.tierPerkRepository = tierPerkRepository;
    }

    /**
     * Calculates membership benefits for an order without requiring a subscription.
     *
     * @param userId user placing the order
     * @param totalAmount original order amount
     * @param category optional category reserved for future category eligibility rules
     * @return benefit snapshot with zero discount and no free delivery for non-members
     * @implNote Used by {@link OrderService#create(OrderDtos.CreateRequest)}.
     */
    @Transactional(readOnly = true)
    public BenefitResult evaluate(UUID userId, BigDecimal totalAmount, String category) {
        return subscriptionService.findActiveSubscription(userId)
            .map(subscription -> evaluateMemberBenefits(subscription, totalAmount))
            .orElseGet(() -> BenefitResult.none(totalAmount));
    }

    /**
     * Calculates discount and free-delivery benefits for an active member.
     *
     * @param subscription active subscription controlling checkout benefits
     * @param totalAmount original order amount
     * @return evaluated benefit snapshot
     * @implNote Used internally after optional subscription resolution.
     */
    private BenefitResult evaluateMemberBenefits(Subscription subscription,
                                                 BigDecimal totalAmount) {
        DiscountDtos.EvaluationResponse discount =
            discountService.evaluate(subscription, totalAmount);
        boolean freeDelivery = tierPerkRepository
            .findAllByTierIdOrderByPerkNameAsc(subscription.getTier().getId())
            .stream()
            .anyMatch(assignment ->
                assignment.getPerk().isActive()
                    && assignment.getPerk().getType() == PerkType.FREE_DELIVERY);
        BigDecimal deliveryFee = freeDelivery
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : STANDARD_DELIVERY_FEE;
        return new BenefitResult(
            discount.discountPercent(),
            discount.discountAmount(),
            discount.appliedPerkCode(),
            discount.appliedPerkName(),
            deliveryFee,
            discount.finalAmount().add(deliveryFee),
            freeDelivery,
            subscription.getTier().getCode()
        );
    }

    /**
     * Defines the membership benefit snapshot persisted with an order.
     */
    public record BenefitResult(
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        String appliedDiscountPerkCode,
        String appliedDiscountPerkName,
        BigDecimal deliveryFee,
        BigDecimal finalAmount,
        boolean freeDelivery,
        String membershipTierCode
    ) {
        public BenefitResult(
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal deliveryFee,
            BigDecimal finalAmount,
            boolean freeDelivery,
            String membershipTierCode
        ) {
            this(discountPercent, discountAmount, null, null, deliveryFee,
                finalAmount, freeDelivery, membershipTierCode);
        }

        /**
         * Creates a no-membership benefit result.
         *
         * @param totalAmount original order amount
         * @return zero-discount result with no delivery benefit or membership tier
         * @implNote Used when order creation finds no active subscription.
         */
        static BenefitResult none(BigDecimal totalAmount) {
            return new BenefitResult(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                null,
                null,
                STANDARD_DELIVERY_FEE,
                totalAmount.add(STANDARD_DELIVERY_FEE)
                    .setScale(2, RoundingMode.HALF_UP),
                false,
                null
            );
        }
    }
}
