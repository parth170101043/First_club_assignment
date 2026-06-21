package com.example.FirstClubApp.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides PostgreSQL persistence, history, locking, and expiry queries for subscriptions.
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Checks whether a user has any current or historical subscription.
     *
     * @param userId user identifier with no default value
     * @return {@code true} when at least one subscription references the user
     * @implNote Used by {@code UserService} to preserve subscription history during deletion.
     */
    boolean existsByUserId(UUID userId);

    /**
     * Checks subscription ownership by identifier and login email.
     *
     * @param id subscription UUID
     * @param email expected owner email
     * @return true when the account owns the subscription
     */
    boolean existsByIdAndUserEmailIgnoreCase(UUID id, String email);

    /**
     * Finds a user's subscription in a specific lifecycle state.
     *
     * @param userId user identifier with no default value
     * @param status requested subscription status
     * @return matching subscription, or an empty optional
     * @implNote Used by {@link SubscriptionService} to retrieve the current subscription.
     */
    Optional<Subscription> findByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    /**
     * Finds a user's complete subscription history in reverse creation order.
     *
     * @param userId user identifier with no default value
     * @return subscriptions ordered newest first, or an empty list
     * @implNote Used by {@link SubscriptionService} for membership history.
     */
    List<Subscription> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Locks and returns the user's active subscription for a lifecycle mutation.
     *
     * @param userId user identifier with no default value
     * @return locked active subscription, or an empty optional
     * @implNote Used by {@link SubscriptionService} to serialize subscribe, cancel, and reactivate operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.user.id = :userId and s.status = 'ACTIVE'")
    Optional<Subscription> findActiveForUpdate(@Param("userId") UUID userId);

    /**
     * Finds active subscriptions whose expiry is at or before the supplied instant.
     *
     * @param status lifecycle status to filter; expiry processing supplies {@code ACTIVE}
     * @param expiresAt inclusive expiry cutoff instant
     * @return due subscriptions, or an empty list
     * @implNote Used by {@link SubscriptionService#expireDueSubscriptions()}.
     */
    List<Subscription> findAllByStatusAndExpiresAtLessThanEqual(
        SubscriptionStatus status, Instant expiresAt);

    List<Subscription> findAllByStatus(SubscriptionStatus status);
}
