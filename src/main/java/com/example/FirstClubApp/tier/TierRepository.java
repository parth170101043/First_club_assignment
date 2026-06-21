package com.example.FirstClubApp.tier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides PostgreSQL persistence and catalogue queries for membership tiers.
 */
public interface TierRepository extends JpaRepository<Tier, UUID> {

    /**
     * Finds selectable tiers ordered from lowest to highest rank.
     *
     * @return active tiers ordered by rank, or an empty list when none exist
     * @implNote Used by {@link TierService} for the public tier catalogue.
     */
    List<Tier> findAllByActiveTrueOrderByRankAsc();

    /**
     * Checks whether a tier code already exists, ignoring letter case.
     *
     * @param code normalized tier code with no default value
     * @return {@code true} when a matching tier exists
     * @implNote Used by {@link TierService} during administrative creation.
     */
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /**
     * Checks whether an ordering rank is already assigned.
     *
     * @param rank positive rank candidate with no default value
     * @return {@code true} when a tier already owns the rank
     * @implNote Used by {@link TierService} during administrative creation.
     */
    boolean existsByRank(int rank);

    /**
     * Finds an active tier by its stable code, ignoring letter case.
     *
     * @param code tier code with no default value
     * @return active matching tier, or an empty optional
     * @implNote Used by independent behavioral tier strategies.
     */
    Optional<Tier> findByCodeIgnoreCaseAndActiveTrue(String code);
}
