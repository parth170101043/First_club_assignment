package com.example.FirstClubApp.perk;

import com.example.FirstClubApp.common.AuditableEntity;
import com.example.FirstClubApp.tier.Tier;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Assigns a reusable perk to a membership tier.
 */
@Entity
@Table(name = "tier_perks")
public class TierPerk extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    private Tier tier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perk_id", nullable = false)
    private Perk perk;

    /**
     * Required by JPA when materializing an assignment from PostgreSQL.
     *
     * @return an assignment with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected TierPerk() {
    }

    /**
     * Creates a tier-to-perk assignment.
     *
     * @param tier membership tier receiving the perk
     * @param perk reusable perk definition
     * @return a new tier-perk assignment
     * @implNote Used by {@link PerkService} for administrative assignment.
     */
    public TierPerk(Tier tier, Perk perk) {
        this.tier = tier;
        this.perk = perk;
    }

    /**
     * Returns the assigned membership tier.
     *
     * @return associated tier
     * @implNote Used by assignment response mapping.
     */
    public Tier getTier() {
        return tier;
    }

    /**
     * Returns the assigned perk definition.
     *
     * @return associated perk
     * @implNote Used by assignment and user-perk response mapping.
     */
    public Perk getPerk() {
        return perk;
    }

    /**
     * Resolves the JSON configuration visible to users of this tier.
     *
     * @return the reusable perk's base configuration
     * @implNote Used by the user-perks API and assignment responses.
     */
    public java.util.Map<String, Object> getEffectiveConfiguration() {
        return perk.getConfiguration();
    }
}
