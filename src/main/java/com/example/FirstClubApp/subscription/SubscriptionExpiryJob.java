package com.example.FirstClubApp.subscription;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically delegates due-subscription processing to the transactional domain service.
 */
@Component
public class SubscriptionExpiryJob {

    private final SubscriptionService subscriptionService;

    /**
     * Creates the scheduled expiry job.
     *
     * @param subscriptionService service that owns expiry state transitions
     * @return an initialized scheduled job
     * @implNote Used by Spring dependency injection during application startup.
     */
    public SubscriptionExpiryJob(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Processes subscriptions whose expiry time has passed.
     *
     * @return no return value
     * @implNote Used by Spring's scheduler every 60 seconds by default; the delay is configurable.
     */
    @Scheduled(fixedDelayString = "${membership.expiry-job-delay-ms:60000}")
    public void expireDueSubscriptions() {
        subscriptionService.expireDueSubscriptions();
    }
}
