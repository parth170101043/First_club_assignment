package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Applies tier catalogue and administration rules between REST controllers and persistence.
 */
@Service
public class TierService {

    private final TierRepository tierRepository;

    /**
     * Creates the tier service.
     *
     * @param tierRepository persistence gateway for tiers
     * @return an initialized tier service
     * @implNote Used by Spring dependency injection when constructing tier components.
     */
    public TierService(TierRepository tierRepository) {
        this.tierRepository = tierRepository;
    }

    /**
     * Returns active tiers ordered by membership rank.
     *
     * @return active tier responses; defaults to an empty list
     * @implNote Used by {@link TierController#findActive()}.
     */
    @Transactional(readOnly = true)
    public List<TierDtos.Response> findActive() {
        return tierRepository.findAllByActiveTrueOrderByRankAsc().stream()
            .map(TierDtos.Response::from)
            .toList();
    }

    /**
     * Returns a tier by identifier.
     *
     * @param id tier UUID with no default value
     * @return matching tier response
     * @implNote Used by {@link TierController#findById(UUID)}.
     */
    @Transactional(readOnly = true)
    public TierDtos.Response findById(UUID id) {
        return TierDtos.Response.from(requireTier(id));
    }

    /**
     * Validates uniqueness and stores a new active tier.
     *
     * @param request validated tier creation request
     * @return created tier response
     * @implNote Used by {@link TierController#create(TierDtos.CreateRequest)}.
     */
    @Transactional
    public TierDtos.Response create(TierDtos.CreateRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (tierRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("A tier with this code already exists.");
        }
        String name = request.name().trim();
        if (tierRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A tier with this name already exists.");
        }
        if (tierRepository.existsByRank(request.rank())) {
            throw new ConflictException("A tier with this rank already exists.");
        }
        Tier tier = new Tier(code, name, request.description(), request.rank(),
            request.monthlyPrice(), request.quarterlyPrice(), request.yearlyPrice());
        return TierDtos.Response.from(tierRepository.save(tier));
    }

    /**
     * Updates mutable configuration for an existing tier.
     *
     * @param id tier UUID with no default value
     * @param request validated tier update request
     * @return updated tier response
     * @implNote Used by {@link TierController#update(UUID, TierDtos.UpdateRequest)}.
     */
    @Transactional
    public TierDtos.Response update(UUID id, TierDtos.UpdateRequest request) {
        Tier tier = requireTier(id);
        String name = request.name().trim();
        if (tierRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A tier with this name already exists.");
        }
        tier.update(name, request.description(), request.monthlyPrice(),
            request.quarterlyPrice(), request.yearlyPrice(), request.active());
        return TierDtos.Response.from(tier);
    }

    /**
     * Updates prices used for future subscriptions while preserving existing price snapshots.
     *
     * @param id tier UUID with no default value
     * @param request validated monthly, quarterly, and yearly prices
     * @return tier response containing the updated prices
     * @implNote Used by the administrative tier pricing controller.
     */
    @Transactional
    public TierDtos.Response updatePrices(UUID id, TierDtos.PriceUpdateRequest request) {
        Tier tier = requireTier(id);
        tier.updatePrices(
            request.monthlyPrice(),
            request.quarterlyPrice(),
            request.yearlyPrice()
        );
        return TierDtos.Response.from(tier);
    }

    /**
     * Loads a tier and verifies that users may currently select it.
     *
     * @param id tier UUID with no default value
     * @return active persistent tier
     * @implNote Used by {@code SubscriptionService} before creating a subscription.
     */
    public Tier requireActiveTier(UUID id) {
        Tier tier = requireTier(id);
        if (!tier.isActive()) {
            throw new ConflictException("The selected tier is inactive.");
        }
        return tier;
    }

    /**
     * Loads a tier entity or raises a domain-level not-found error.
     *
     * @param id tier UUID with no default value
     * @return matching persistent tier
     * @implNote Used internally by tier queries, updates, and subscription validation.
     */
    public Tier requireTier(UUID id) {
        return tierRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tier not found: " + id));
    }

    /**
     * Loads an active tier using its stable code.
     *
     * @param code configured behavioral target tier code
     * @return active persistent tier
     * @implNote Used by order-count, monthly-spend, and cohort strategies.
     */
    public Tier requireActiveTierByCode(String code) {
        return tierRepository.findByCodeIgnoreCaseAndActiveTrue(code)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Active tier not found for code: " + code));
    }
}
