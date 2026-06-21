package com.example.FirstClubApp.perk;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

/**
 * Exposes perks available to a user through the user's current active subscription tier.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/perks")
public class UserPerkController {

    private final PerkService perkService;

    /**
     * Creates the user-perk controller.
     *
     * @param perkService service that resolves active subscription entitlements
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public UserPerkController(PerkService perkService) {
        this.perkService = perkService;
    }

    /**
     * Returns active perks assigned to the user's current subscription tier.
     *
     * @param userId user UUID supplied in the URL path
     * @return subscription context and effective perk configurations
     * @implNote Used by membership home screens and future checkout integrations.
     */
    @GetMapping
    @PreAuthorize("@accountAuthorization.canAccess(#userId, authentication)")
    PerkDtos.UserPerksResponse getUserPerks(@PathVariable UUID userId) {
        return perkService.getUserPerks(userId);
    }
}
