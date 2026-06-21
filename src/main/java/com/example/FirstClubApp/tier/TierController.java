package com.example.FirstClubApp.tier;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 * Exposes REST operations for the tier catalogue and temporary tier administration.
 */
@RestController
@RequestMapping("/api/v1/tiers")
public class TierController {

    private final TierService tierService;

    /**
     * Creates the tier REST controller.
     *
     * @param tierService service that owns tier rules and persistence
     * @return an initialized controller
     * @implNote Used by Spring MVC during application startup.
     */
    public TierController(TierService tierService) {
        this.tierService = tierService;
    }

    /**
     * Lists active tiers from lowest to highest rank.
     *
     * @return active tier responses; defaults to an empty list
     * @implNote Used by membership selection screens and API clients.
     */
    @GetMapping
    List<TierDtos.Response> findActive() {
        return tierService.findActive();
    }

    /**
     * Retrieves one tier by UUID.
     *
     * @param id tier UUID supplied in the URL path
     * @return matching tier response
     * @implNote Used by membership detail screens and API clients.
     */
    @GetMapping("/{id}")
    TierDtos.Response findById(@PathVariable UUID id) {
        return tierService.findById(id);
    }

    /**
     * Creates a configurable membership tier.
     *
     * @param request validated tier configuration parsed from JSON
     * @return created tier response with HTTP 201
     * @implNote Used by temporary administrative clients until role security is added.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TierDtos.Response create(@Valid @RequestBody TierDtos.CreateRequest request) {
        return tierService.create(request);
    }

    /**
     * Updates mutable configuration for a membership tier.
     *
     * @param id tier UUID supplied in the URL path
     * @param request validated updated tier configuration
     * @return updated tier response
     * @implNote Used by temporary administrative clients until role security is added.
     */
    @PutMapping("/{id}")
    TierDtos.Response update(@PathVariable UUID id,
                             @Valid @RequestBody TierDtos.UpdateRequest request) {
        return tierService.update(id, request);
    }
}
