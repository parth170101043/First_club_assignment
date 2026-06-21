package com.example.FirstClubApp.subscription;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that the scheduler delegates expiry processing to the transactional service.
 */
class SubscriptionExpiryJobTest {

    /**
     * Verifies one scheduled invocation triggers one service expiry run.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect scheduled job delegation.
     */
    @Test
    void delegatesExpiryProcessing() {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        SubscriptionExpiryJob job = new SubscriptionExpiryJob(subscriptionService);

        job.expireDueSubscriptions();

        verify(subscriptionService).expireDueSubscriptions();
    }
}
