package com.example.FirstClubApp.testutil;

import com.example.FirstClubApp.common.AuditableEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * Initializes persistence-managed fields on in-memory entities used by unit tests.
 */
public final class TestEntityFactory {

    /**
     * Prevents instantiation of this test utility namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime when enforcing the private constructor.
     */
    private TestEntityFactory() {
    }

    /**
     * Assigns an identifier and audit timestamps normally populated by JPA.
     *
     * @param entity entity under test
     * @param id UUID to assign with no default value
     * @return the same initialized entity for fluent test setup
     * @param <T> concrete auditable entity type
     * @implNote Used by service and controller unit tests that do not start PostgreSQL.
     */
    public static <T extends AuditableEntity> T initialize(T entity, UUID id) {
        Instant timestamp = Instant.parse("2026-06-21T06:30:00Z");
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", timestamp);
        ReflectionTestUtils.setField(entity, "updatedAt", timestamp);
        return entity;
    }
}
