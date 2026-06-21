package com.example.FirstClubApp.admin;

import com.example.FirstClubApp.perk.PerkDtos;
import com.example.FirstClubApp.perk.PerkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exposes administrative REST operations for assigning reusable perks to membership tiers.
 */
@RestController
@RequestMapping("/api/v1/admin/tiers/{tierId}/perks")
public class AdminTierPerkController {

    private final PerkService perkService;

    /**
     * Creates the administrative tier-perk controller.
     *
     * @param perkService service that owns tier assignment rules
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public AdminTierPerkController(PerkService perkService) {
        this.perkService = perkService;
    }

    /**
     * Lists all perk assignments for a tier.
     *
     * @param tierId tier UUID supplied in the URL path
     * @return assignments ordered by perk name; defaults to an empty list
     * @implNote Used by administrative tier configuration screens.
     */
    @GetMapping
    List<PerkDtos.AssignmentResponse> findForTier(@PathVariable UUID tierId) {
        return perkService.findForTier(tierId);
    }

    /**
     * Creates or reuses a perk assignment.
     *
     * @param tierId tier UUID supplied in the URL path
     * @param perkId perk UUID supplied in the URL path
     * @param request optional tier-specific configuration parsed from JSON
     * @return created or updated assignment response
     * @implNote Used by administrators adding a perk to a subscription tier.
     */
    @PutMapping("/{perkId}")
    PerkDtos.AssignmentResponse assign(
        @PathVariable UUID tierId,
        @PathVariable UUID perkId,
        @Valid @RequestBody PerkDtos.AssignmentRequest request) {
        return perkService.assign(tierId, perkId, request);
    }

    /**
     * Removes a perk from a tier while leaving the reusable perk in the catalogue.
     *
     * @param tierId tier UUID supplied in the URL path
     * @param perkId perk UUID supplied in the URL path
     * @return no response body with HTTP 204
     * @implNote Used by administrators removing a perk from a subscription tier.
     */
    @DeleteMapping("/{perkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unassign(@PathVariable UUID tierId, @PathVariable UUID perkId) {
        perkService.unassign(tierId, perkId);
    }
}
