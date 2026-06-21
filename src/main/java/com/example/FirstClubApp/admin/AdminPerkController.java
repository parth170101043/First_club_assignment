package com.example.FirstClubApp.admin;

import com.example.FirstClubApp.perk.PerkDtos;
import com.example.FirstClubApp.perk.PerkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exposes administrative REST operations for the independent perk catalogue.
 */
@RestController
@RequestMapping("/api/v1/admin/perks")
public class AdminPerkController {

    private final PerkService perkService;

    /**
     * Creates the administrative perk controller.
     *
     * @param perkService service that owns perk catalogue and assignment rules
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public AdminPerkController(PerkService perkService) {
        this.perkService = perkService;
    }

    /**
     * Creates a reusable perk that may remain unassigned to every tier.
     *
     * @param request validated perk definition parsed from JSON
     * @return created perk response with HTTP 201
     * @implNote Used by administrators configuring the perk catalogue.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PerkDtos.Response create(@Valid @RequestBody PerkDtos.CreateRequest request) {
        return perkService.create(request);
    }

    /**
     * Lists active, inactive, assigned, and unassigned perks.
     *
     * @return complete perk catalogue ordered by name; defaults to an empty list
     * @implNote Used by administrative catalogue screens.
     */
    @GetMapping
    List<PerkDtos.Response> findAll() {
        return perkService.findAll();
    }

    /**
     * Retrieves one perk by UUID.
     *
     * @param perkId perk UUID supplied in the URL path
     * @return matching perk response
     * @implNote Used by administrative perk edit screens.
     */
    @GetMapping("/{perkId}")
    PerkDtos.Response findById(@PathVariable UUID perkId) {
        return perkService.findById(perkId);
    }

    /**
     * Updates mutable fields and activation state for a perk.
     *
     * @param perkId perk UUID supplied in the URL path
     * @param request validated updated perk configuration
     * @return updated perk response
     * @implNote Used by administrators editing reusable perk definitions.
     */
    @PutMapping("/{perkId}")
    PerkDtos.Response update(@PathVariable UUID perkId,
                             @Valid @RequestBody PerkDtos.UpdateRequest request) {
        return perkService.update(perkId, request);
    }

    /**
     * Deactivates a perk without deleting its definition or assignments.
     *
     * @param perkId perk UUID supplied in the URL path
     * @return no response body with HTTP 204
     * @implNote Used by administrators removing a perk from user availability.
     */
    @DeleteMapping("/{perkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable UUID perkId) {
        perkService.deactivate(perkId);
    }
}
