package com.example.FirstClubApp.perk;

import com.example.FirstClubApp.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Stores a reusable perk definition that may exist independently of any membership tier.
 */
@Entity
@Table(name = "perks")
public class Perk extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "perk_type", nullable = false, length = 40)
    private PerkType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Required by JPA when materializing a perk from PostgreSQL.
     *
     * @return a perk with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected Perk() {
    }

    /**
     * Creates an active reusable perk.
     *
     * @param code unique machine-readable perk code
     * @param name display name
     * @param description optional explanation; defaults to {@code null}
     * @param type behavior category with no default value
     * @param configuration JSON configuration; defaults to an empty object when {@code null}
     * @return a new active perk
     * @implNote Used by {@link PerkService} when an administrator creates a perk.
     */
    public Perk(String code, String name, String description, PerkType type,
                Map<String, Object> configuration) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.type = type;
        this.configuration = configuration == null ? Map.of() : configuration;
    }

    /**
     * Updates mutable perk configuration and activation state.
     *
     * @param name updated display name
     * @param description updated optional explanation; may be {@code null}
     * @param type updated behavior category
     * @param configuration updated JSON configuration; defaults to an empty object when {@code null}
     * @param active whether the perk is available through tier assignments
     * @return no return value
     * @implNote Used by {@link PerkService} for administrative edits and deactivation.
     */
    public void update(String name, String description, PerkType type,
                       Map<String, Object> configuration, boolean active) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.configuration = configuration == null ? Map.of() : configuration;
        this.active = active;
    }

    /**
     * Deactivates the perk without deleting its historical definition.
     *
     * @return no return value
     * @implNote Used by {@link PerkService} for the admin remove operation.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Returns the unique perk code.
     *
     * @return machine-readable code
     * @implNote Used by response mappers and future checkout benefit evaluation.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the perk display name.
     *
     * @return display name
     * @implNote Used by admin and user response mappers.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the optional perk description.
     *
     * @return description, or {@code null} when absent
     * @implNote Used by admin and user response mappers.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the perk behavior category.
     *
     * @return configured perk type
     * @implNote Used by clients and future benefit evaluators.
     */
    public PerkType getType() {
        return type;
    }

    /**
     * Returns the base JSON configuration.
     *
     * @return configuration object; defaults to an empty JSON object
     * @implNote Used by assignment response mappers and discount evaluation.
     */
    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    /**
     * Reports whether the perk is currently available.
     *
     * @return {@code true} by default for a newly created perk
     * @implNote Used by user-perk filtering and administrative responses.
     */
    public boolean isActive() {
        return active;
    }
}
