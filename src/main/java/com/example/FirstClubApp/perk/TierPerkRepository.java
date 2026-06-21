package com.example.FirstClubApp.perk;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides PostgreSQL persistence and tier-assignment queries for perks.
 */
public interface TierPerkRepository extends JpaRepository<TierPerk, UUID> {

    /**
     * Checks whether a perk is still assigned to any membership tier.
     *
     * @param perkId perk UUID
     * @return true when at least one assignment exists
     */
    boolean existsByPerkId(UUID perkId);

    /**
     * Finds one assignment by its tier and perk identifiers.
     *
     * @param tierId tier UUID with no default value
     * @param perkId perk UUID with no default value
     * @return matching assignment, or an empty optional
     * @implNote Used by {@link PerkService} to update or remove an assignment.
     */
    @EntityGraph(attributePaths = {"tier", "perk"})
    Optional<TierPerk> findByTierIdAndPerkId(UUID tierId, UUID perkId);

    /**
     * Lists every perk assignment for a tier with required associations preloaded.
     *
     * @param tierId tier UUID with no default value
     * @return tier assignments ordered by perk name; defaults to an empty list
     * @implNote Used by admin tier-perk and user-perk APIs.
     */
    @EntityGraph(attributePaths = {"tier", "perk"})
    List<TierPerk> findAllByTierIdOrderByPerkNameAsc(UUID tierId);
}
