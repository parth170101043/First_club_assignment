package com.example.FirstClubApp.payment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Provides persistence and user history queries for payment transactions.
 */
public interface PaymentTransactionRepository
    extends JpaRepository<PaymentTransaction, UUID> {

    /**
     * Checks whether a user has payment transaction history.
     *
     * @param userId user UUID with no default value
     * @return {@code true} when at least one transaction references the user
     * @implNote Used by user deletion safeguards.
     */
    boolean existsByUserId(UUID userId);

    /**
     * Lists a user's payment transactions with required associations loaded.
     *
     * @param userId user UUID with no default value
     * @return transactions ordered newest first; defaults to an empty list
     * @implNote Used by {@link PaymentService} for payment history.
     */
    @EntityGraph(attributePaths = {"user", "paymentMethod"})
    List<PaymentTransaction> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
