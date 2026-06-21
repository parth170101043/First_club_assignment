package com.example.FirstClubApp.auth;

import com.example.FirstClubApp.user.User;
import com.example.FirstClubApp.user.UserRepository;
import com.example.FirstClubApp.subscription.SubscriptionRepository;
import com.example.FirstClubApp.order.OrderRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enforces ownership for APIs that still contain a user UUID in their route or payload.
 */
@Component
public class AccountAuthorization {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;

    public AccountAuthorization(UserRepository userRepository,
                                SubscriptionRepository subscriptionRepository,
                                OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Requires the authenticated member to own the requested account, unless they are an admin.
     *
     * @param userId requested account UUID
     * @param authentication current Spring Security authentication
     */
    public void requireSelfOrAdmin(UUID userId, Authentication authentication) {
        if (authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return;
        }
        User account = userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new AccessDeniedException("Authenticated account was not found."));
        if (!account.getId().equals(userId)) {
            throw new AccessDeniedException("You cannot access another user's account.");
        }
    }

    /**
     * Returns whether the current account owns the requested user UUID or is an administrator.
     *
     * @param userId requested account UUID
     * @param authentication current authentication
     * @return true when access is allowed
     */
    public boolean canAccess(UUID userId, Authentication authentication) {
        try {
            requireSelfOrAdmin(userId, authentication);
            return true;
        } catch (AccessDeniedException exception) {
            return false;
        }
    }

    public boolean canAccessSubscription(UUID subscriptionId, Authentication authentication) {
        return isAdmin(authentication)
            || subscriptionRepository.existsByIdAndUserEmailIgnoreCase(
                subscriptionId, authentication.getName());
    }

    public boolean canAccessOrder(UUID orderId, Authentication authentication) {
        return isAdmin(authentication)
            || orderRepository.existsByIdAndUserEmailIgnoreCase(
                orderId, authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
