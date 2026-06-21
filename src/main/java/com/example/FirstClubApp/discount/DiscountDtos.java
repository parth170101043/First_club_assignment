package com.example.FirstClubApp.discount;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Groups validated contracts for subscription-perk order discount evaluation.
 */
public final class DiscountDtos {

    /**
     * Prevents instantiation of this DTO namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime when enforcing the private constructor.
     */
    private DiscountDtos() {
    }

    /**
     * Defines the order amount submitted for discount evaluation.
     */
    public record EvaluationRequest(
        @NotNull @DecimalMin("0.00") BigDecimal orderAmount
    ) {
    }

    /**
     * Defines the calculated discount for a user's active subscription.
     */
    public record EvaluationResponse(
        UUID userId,
        UUID subscriptionId,
        UUID tierId,
        BigDecimal orderAmount,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        boolean applied,
        String appliedPerkCode,
        String appliedPerkName
    ) {
        public EvaluationResponse(
            UUID userId,
            UUID subscriptionId,
            UUID tierId,
            BigDecimal orderAmount,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            boolean applied
        ) {
            this(userId, subscriptionId, tierId, orderAmount, discountPercent,
                discountAmount, finalAmount, applied, null, null);
        }
    }
}
