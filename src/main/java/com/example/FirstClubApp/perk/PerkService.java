package com.example.FirstClubApp.perk;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.subscription.Subscription;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.tier.Tier;
import com.example.FirstClubApp.tier.TierService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Manages the independent perk catalogue, tier assignments, and user entitlement resolution.
 */
@Service
public class PerkService {

    private final PerkRepository perkRepository;
    private final TierPerkRepository tierPerkRepository;
    private final TierService tierService;
    private final SubscriptionService subscriptionService;

    /**
     * Creates the perk service.
     *
     * @param perkRepository persistence gateway for reusable perk definitions
     * @param tierPerkRepository persistence gateway for tier assignments
     * @param tierService tier lookup and validation service
     * @param subscriptionService active subscription lookup and expiry service
     * @return an initialized perk service
     * @implNote Used by Spring dependency injection for admin and user perk controllers.
     */
    public PerkService(PerkRepository perkRepository,
                       TierPerkRepository tierPerkRepository,
                       TierService tierService,
                       SubscriptionService subscriptionService) {
        this.perkRepository = perkRepository;
        this.tierPerkRepository = tierPerkRepository;
        this.tierService = tierService;
        this.subscriptionService = subscriptionService;
    }

    /**
     * Creates an active perk that may initially remain unassigned.
     *
     * @param request validated perk definition
     * @return created perk response
     * @implNote Used by the admin perk creation endpoint.
     */
    @Transactional
    public PerkDtos.Response create(PerkDtos.CreateRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (perkRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("A perk with this code already exists.");
        }
        String name = request.name().trim();
        if (perkRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A perk with this name already exists.");
        }
        Perk perk = new Perk(code, name, trimToNull(request.description()),
            request.type(), request.configuration());
        return PerkDtos.Response.from(perkRepository.save(perk));
    }

    /**
     * Lists the complete admin catalogue, including inactive and unassigned perks.
     *
     * @return perk responses ordered by name; defaults to an empty list
     * @implNote Used by the admin perk catalogue endpoint.
     */
    @Transactional(readOnly = true)
    public List<PerkDtos.Response> findAll() {
        return perkRepository.findAllByOrderByNameAsc().stream()
            .map(PerkDtos.Response::from)
            .toList();
    }

    /**
     * Returns one perk definition by identifier.
     *
     * @param perkId perk UUID with no default value
     * @return matching perk response
     * @implNote Used by the admin perk detail endpoint.
     */
    @Transactional(readOnly = true)
    public PerkDtos.Response findById(UUID perkId) {
        return PerkDtos.Response.from(requirePerk(perkId));
    }

    /**
     * Updates mutable fields of a reusable perk.
     *
     * @param perkId perk UUID with no default value
     * @param request validated update request
     * @return updated perk response
     * @implNote Used by the admin perk update endpoint.
     */
    @Transactional
    public PerkDtos.Response update(UUID perkId, PerkDtos.UpdateRequest request) {
        Perk perk = requirePerk(perkId);
        String name = request.name().trim();
        if (perkRepository.existsByNameIgnoreCaseAndIdNot(name, perkId)) {
            throw new ConflictException("A perk with this name already exists.");
        }
        perk.update(name, trimToNull(request.description()), request.type(),
            request.configuration(), request.active());
        return PerkDtos.Response.from(perk);
    }

    /**
     * Deactivates a perk while retaining catalogue and assignment history.
     *
     * @param perkId perk UUID with no default value
     * @return no return value
     * @implNote Used by the admin perk remove endpoint.
     */
    @Transactional
    public void deactivate(UUID perkId) {
        Perk perk = requirePerk(perkId);
        if (tierPerkRepository.existsByPerkId(perkId)) {
            throw new ConflictException(
                "Remove this perk from every membership tier before deleting it.");
        }
        perkRepository.delete(perk);
        perkRepository.flush();
    }

    /**
     * Creates or updates a perk assignment for a membership tier.
     *
     * @param tierId tier UUID with no default value
     * @param perkId perk UUID with no default value
     * @param request empty assignment request; configuration is owned by the perk
     * @return created or updated assignment response
     * @implNote Used by the admin tier-perk assignment endpoint.
     */
    @Transactional
    public PerkDtos.AssignmentResponse assign(UUID tierId, UUID perkId,
                                              PerkDtos.AssignmentRequest request) {
        Tier tier = tierService.requireTier(tierId);
        Perk perk = requireActivePerk(perkId);
        TierPerk assignment = tierPerkRepository.findByTierIdAndPerkId(tierId, perkId)
            .orElseGet(() -> new TierPerk(tier, perk));
        return PerkDtos.AssignmentResponse.from(tierPerkRepository.save(assignment));
    }

    /**
     * Removes a perk assignment from a tier without deleting the reusable perk.
     *
     * @param tierId tier UUID with no default value
     * @param perkId perk UUID with no default value
     * @return no return value
     * @implNote Used by the admin tier-perk removal endpoint.
     */
    @Transactional
    public void unassign(UUID tierId, UUID perkId) {
        TierPerk assignment = tierPerkRepository.findByTierIdAndPerkId(tierId, perkId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Perk is not assigned to tier: " + tierId));
        tierPerkRepository.delete(assignment);
        tierPerkRepository.flush();
    }

    /**
     * Lists all assignments for a tier, including assignments to inactive perks.
     *
     * @param tierId tier UUID with no default value
     * @return assignment responses ordered by perk name; defaults to an empty list
     * @implNote Used by the admin tier-perk listing endpoint.
     */
    @Transactional(readOnly = true)
    public List<PerkDtos.AssignmentResponse> findForTier(UUID tierId) {
        tierService.requireTier(tierId);
        return tierPerkRepository.findAllByTierIdOrderByPerkNameAsc(tierId).stream()
            .map(PerkDtos.AssignmentResponse::from)
            .toList();
    }

    /**
     * Resolves active perks available through a user's current subscription tier.
     *
     * @param userId user UUID with no default value
     * @return user, subscription, tier, expiry, and active perk assignments
     * @implNote Used by the user-perks endpoint; unassigned and inactive perks are excluded.
     */
    @Transactional
    public PerkDtos.UserPerksResponse getUserPerks(UUID userId) {
        Subscription subscription = subscriptionService.requireActiveSubscription(userId);
        List<PerkDtos.AssignmentResponse> perks = tierPerkRepository
            .findAllByTierIdOrderByPerkNameAsc(subscription.getTier().getId())
            .stream()
            .filter(assignment -> assignment.getPerk().isActive())
            .map(PerkDtos.AssignmentResponse::from)
            .toList();
        return PerkDtos.UserPerksResponse.from(subscription, perks);
    }

    /**
     * Loads a perk entity or raises a domain-level not-found error.
     *
     * @param perkId perk UUID with no default value
     * @return matching persistent perk
     * @implNote Used internally by all catalogue and assignment mutations.
     */
    private Perk requirePerk(UUID perkId) {
        return perkRepository.findById(perkId)
            .orElseThrow(() -> new ResourceNotFoundException("Perk not found: " + perkId));
    }

    /**
     * Loads a perk and verifies that it can be newly assigned.
     *
     * @param perkId perk UUID with no default value
     * @return active persistent perk
     * @implNote Used internally by tier assignment creation and update.
     */
    private Perk requireActivePerk(UUID perkId) {
        Perk perk = requirePerk(perkId);
        if (!perk.isActive()) {
            throw new ConflictException("Inactive perks cannot be assigned to tiers.");
        }
        return perk;
    }

    /**
     * Trims optional text and converts blank input to {@code null}.
     *
     * @param value optional source text; defaults to {@code null}
     * @return trimmed text, or {@code null} when absent or blank
     * @implNote Used internally while normalizing optional perk descriptions.
     */
    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
