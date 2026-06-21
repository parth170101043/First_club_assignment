package com.example.FirstClubApp.perk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides PostgreSQL persistence and uniqueness queries for the independent perk catalogue.
 */
public interface PerkRepository extends JpaRepository<Perk, UUID> {

    /**
     * Checks whether a perk code already exists, ignoring letter case.
     *
     * @param code normalized perk code with no default value
     * @return {@code true} when a matching perk exists
     * @implNote Used by {@link PerkService} before administrative perk creation.
     */
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /**
     * Finds a reusable perk by its code, ignoring letter case.
     *
     * @param code perk code with no default value
     * @return matching perk, or an empty optional
     * @implNote Used by configurable discount administration to reuse one system perk definition.
     */
    Optional<Perk> findByCodeIgnoreCase(String code);

    /**
     * Lists all perks in stable name order, including unassigned and inactive perks.
     *
     * @return complete perk catalogue ordered by name; defaults to an empty list
     * @implNote Used by {@link PerkService} for administrative catalogue retrieval.
     */
    List<Perk> findAllByOrderByNameAsc();
}
