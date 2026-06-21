package com.example.FirstClubApp.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

/**
 * Groups validated request and response contracts used by the order REST API.
 */
public final class OrderDtos {

    /**
     * Prevents instantiation of this DTO namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime when enforcing the private constructor.
     */
    private OrderDtos() {
    }

    /**
     * Defines the user, order amount, and optional category accepted during order creation.
     */
    public record CreateRequest(
        @NotNull UUID userId,
        @NotNull @DecimalMin("0.00") BigDecimal totalAmount,
        @Size(max = 100) String category
    ) {
    }

    /**
     * Defines the persisted order and membership benefit snapshot returned by the API.
     */
    public record Response(
        UUID id,
        UUID userId,
        BigDecimal totalAmount,
        String category,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        String appliedDiscountPerkCode,
        String appliedDiscountPerkName,
        BigDecimal deliveryFee,
        BigDecimal finalAmount,
        boolean freeDelivery,
        String membershipTierCode,
        List<ItemResponse> items,
        Instant createdAt
    ) {

        public Response(
            UUID id, UUID userId, BigDecimal totalAmount, String category,
            BigDecimal discountPercent, BigDecimal discountAmount,
            BigDecimal finalAmount, boolean freeDelivery,
            String membershipTierCode, Instant createdAt
        ) {
            this(id, userId, totalAmount, category, discountPercent,
                discountAmount, null, null, BigDecimal.ZERO, finalAmount, freeDelivery,
                membershipTierCode, List.of(), createdAt);
        }

        /**
         * Maps a persistent order to its API response.
         *
         * @param order initialized order entity
         * @return immutable order response
         * @implNote Used by {@link OrderService} for creation, lookup, and history operations.
         */
        static Response from(CustomerOrder order) {
            return new Response(
                order.getId(),
                order.getUser().getId(),
                order.getTotalAmount(),
                order.getCategory(),
                order.getDiscountPercent(),
                order.getDiscountAmount(),
                order.getAppliedDiscountPerkCode(),
                order.getAppliedDiscountPerkName(),
                order.getDeliveryFee(),
                order.getFinalAmount(),
                order.isFreeDelivery(),
                order.getMembershipTierCode(),
                order.getItems().stream().map(ItemResponse::from).toList(),
                order.getCreatedAt()
            );
        }
    }

    public record ItemResponse(
        String sku,
        String name,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
    ) {
        static ItemResponse from(OrderItem item) {
            return new ItemResponse(
                item.getSkuSnapshot(), item.getNameSnapshot(),
                item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
        }
    }
}
