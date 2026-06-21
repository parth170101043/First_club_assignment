package com.example.FirstClubApp.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Provides PostgreSQL persistence and user history queries for completed orders.
 */
public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    /**
     * Checks whether a user has any persisted order history.
     *
     * @param userId user UUID with no default value
     * @return {@code true} when at least one order references the user
     * @implNote Used by user deletion safeguards.
     */
    boolean existsByUserId(UUID userId);

    /**
     * Checks order ownership by identifier and login email.
     *
     * @param id order UUID
     * @param email expected owner email
     * @return true when the account owns the order
     */
    boolean existsByIdAndUserEmailIgnoreCase(UUID id, String email);

    /**
     * Lists a user's orders in reverse creation order with the user association loaded.
     *
     * @param userId user UUID with no default value
     * @return orders ordered newest first; defaults to an empty list
     * @implNote Used by {@link OrderService} for user order history.
     */
    @EntityGraph(attributePaths = {"user", "items"})
    List<CustomerOrder> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Counts orders created by a user from a supplied instant onward.
     *
     * @param userId user UUID with no default value
     * @param startsAt inclusive behavioral evaluation window start
     * @return order count; defaults to {@code 0}
     * @implNote Used by the order-count behavioral tier strategy.
     */
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant startsAt);

    /**
     * Sums original order amounts for a user from a supplied instant onward.
     *
     * @param userId user UUID with no default value
     * @param startsAt inclusive behavioral evaluation window start
     * @return total original spend; defaults to zero
     * @implNote Used by the monthly-spend behavioral tier strategy.
     */
    @Query("""
        select coalesce(sum(order.totalAmount), 0)
        from CustomerOrder order
        where order.user.id = :userId
          and order.createdAt >= :startsAt
        """)
    BigDecimal sumTotalAmountSince(
        @Param("userId") UUID userId,
        @Param("startsAt") Instant startsAt);
}
