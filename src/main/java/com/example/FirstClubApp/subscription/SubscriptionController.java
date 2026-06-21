package com.example.FirstClubApp.subscription;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

/**
 * Exposes REST operations for subscription creation, queries, cancellation, and expiry.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Creates the subscription REST controller.
     *
     * @param subscriptionService service that owns the subscription lifecycle
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Creates a new active membership subscription.
     *
     * @param request validated user, plan, and paid-tier selection
     * @return created subscription response with HTTP 201
     * @implNote Used by membership purchase clients.
     */
    @PostMapping
    @PreAuthorize("@accountAuthorization.canAccess(#request.userId(), authentication)")
    @ResponseStatus(HttpStatus.CREATED)
    SubscriptionDtos.Response subscribe(
        @Valid @RequestBody SubscriptionDtos.CreateRequest request) {
        return subscriptionService.subscribe(request);
    }

    /**
     * Immediately upgrades the paid minimum tier after a mock payment.
     *
     * @param subscriptionId active subscription UUID
     * @param request higher tier and payment method
     * @return upgraded subscription response
     * @implNote Used by members purchasing a higher paid tier.
     */
    @PostMapping("/{subscriptionId}/upgrade")
    @PreAuthorize("@accountAuthorization.canAccessSubscription(#subscriptionId, authentication)")
    SubscriptionDtos.Response upgrade(
        @PathVariable UUID subscriptionId,
        @Valid @RequestBody SubscriptionDtos.UpgradeRequest request) {
        return subscriptionService.upgrade(subscriptionId, request);
    }

    /**
     * Schedules a paid minimum tier downgrade for the next renewal.
     *
     * @param subscriptionId active subscription UUID
     * @param request lower tier selection
     * @return subscription response containing the scheduled tier
     * @implNote Used by members who should retain paid benefits for the current period.
     */
    @PostMapping("/{subscriptionId}/downgrade")
    @PreAuthorize("@accountAuthorization.canAccessSubscription(#subscriptionId, authentication)")
    SubscriptionDtos.Response downgrade(
        @PathVariable UUID subscriptionId,
        @Valid @RequestBody SubscriptionDtos.DowngradeRequest request) {
        return subscriptionService.downgrade(subscriptionId, request);
    }

    /**
     * Retrieves a user's current active subscription.
     *
     * @param userId user UUID supplied in the URL path
     * @return current subscription response
     * @implNote Used by the membership home screen.
     */
    @GetMapping("/users/{userId}/current")
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    SubscriptionDtos.Response currentForUser(@PathVariable UUID userId) {
        return subscriptionService.currentForUser(userId);
    }

    /**
     * Retrieves a user's subscription history.
     *
     * @param userId user UUID supplied in the URL path
     * @return subscriptions ordered newest first; defaults to an empty list
     * @implNote Used by membership history screens and support clients.
     */
    @GetMapping("/users/{userId}/history")
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    List<SubscriptionDtos.Response> historyForUser(@PathVariable UUID userId) {
        return subscriptionService.historyForUser(userId);
    }

    /**
     * Schedules an active subscription for period-end cancellation.
     *
     * @param subscriptionId subscription UUID supplied in the URL path
     * @return updated subscription response
     * @implNote Used by members who cancel without receiving a refund.
     */
    @PostMapping("/{subscriptionId}/cancel")
    @PreAuthorize("@accountAuthorization.canAccessSubscription(#subscriptionId, authentication)")
    SubscriptionDtos.Response cancel(@PathVariable UUID subscriptionId) {
        return subscriptionService.cancel(subscriptionId);
    }

    /**
     * Removes a pending cancellation before the subscription expires.
     *
     * @param subscriptionId subscription UUID supplied in the URL path
     * @return reactivated subscription response
     * @implNote Used by members who reverse a scheduled cancellation.
     */
    @PostMapping("/{subscriptionId}/reactivate")
    @PreAuthorize("@accountAuthorization.canAccessSubscription(#subscriptionId, authentication)")
    SubscriptionDtos.Response reactivate(@PathVariable UUID subscriptionId) {
        return subscriptionService.reactivate(subscriptionId);
    }

    /**
     * Manually processes due subscriptions.
     *
     * @return count of subscriptions changed by the expiry run
     * @implNote Used by developers and support diagnostics; the scheduled job normally performs this work.
     */
    @PostMapping("/expire-due")
    ExpiryResponse expireDue() {
        return new ExpiryResponse(subscriptionService.expireDueSubscriptions());
    }

    /**
     * Represents the result of a manual expiry-processing request.
     *
     * @param expiredSubscriptions number processed; defaults to {@code 0} when none are due
     */
    record ExpiryResponse(int expiredSubscriptions) {
    }
}
