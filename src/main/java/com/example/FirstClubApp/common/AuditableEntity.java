package com.example.FirstClubApp.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Provides identifiers, optimistic-lock versions, and audit timestamps for persistent entities.
 */
@MappedSuperclass
public abstract class AuditableEntity {

    @Id
    @Column(nullable = false, updatable = false)
    protected UUID id;

    @Version
    @Column(nullable = false)
    protected long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt;

    /**
     * Initializes the identifier and audit timestamps before the entity is inserted.
     *
     * @return no return value
     * @implNote Used by the JPA persistence provider during entity creation.
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Refreshes the update timestamp before an existing entity is persisted.
     *
     * @return no return value
     * @implNote Used by the JPA persistence provider during entity updates.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Returns the persistent entity identifier.
     *
     * @return the UUID identifier; it is generated automatically before the first insert
     * @implNote Used by services and response mappers that expose entity identifiers.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the optimistic-lock version.
     *
     * @return the current version; its initial database value is {@code 0}
     * @implNote Used by JPA for concurrency control and by subscription responses.
     */
    public long getVersion() {
        return version;
    }

    /**
     * Returns the entity creation time.
     *
     * @return the UTC creation instant generated before insertion
     * @implNote Used by API response mappers and audit consumers.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the most recent entity update time.
     *
     * @return the UTC update instant generated before insertion or update
     * @implNote Used by audit consumers and persistence diagnostics.
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
