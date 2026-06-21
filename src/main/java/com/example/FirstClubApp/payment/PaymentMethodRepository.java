package com.example.FirstClubApp.payment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence and ownership queries for user payment methods.
 */
public interface PaymentMethodRepository extends JpaRepository<UserPaymentMethod, UUID> {

    /**
     * Checks whether a mock provider token has already been stored.
     *
     * @param providerToken provider token with no default value
     * @return {@code true} when the token already exists
     * @implNote Used by {@link PaymentService} to prevent duplicate method registration.
     */
    boolean existsByProviderToken(String providerToken);

    /**
     * Lists active payment methods for a user with owners preloaded.
     *
     * @param userId user UUID with no default value
     * @return active methods ordered by default status and creation time
     * @implNote Used by {@link PaymentService} for method listing and default maintenance.
     */
    @EntityGraph(attributePaths = "user")
    List<UserPaymentMethod> findAllByUserIdAndActiveTrueOrderByDefaultMethodDescCreatedAtAsc(
        UUID userId);

    /**
     * Finds an active payment method owned by one user.
     *
     * @param id payment method UUID
     * @param userId expected owner UUID
     * @return matching active method, or an empty optional
     * @implNote Used by {@link PaymentService} for charge and removal ownership checks.
     */
    @EntityGraph(attributePaths = "user")
    Optional<UserPaymentMethod> findByIdAndUserIdAndActiveTrue(UUID id, UUID userId);
}
