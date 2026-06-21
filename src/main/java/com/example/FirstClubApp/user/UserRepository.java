package com.example.FirstClubApp.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Provides PostgreSQL persistence operations and email uniqueness checks for users.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Checks whether an email is already registered, ignoring letter case.
     *
     * @param email normalized email candidate with no default value
     * @return {@code true} when a matching user exists
     * @implNote Used by {@link UserService} before creating a user.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Finds an account by its case-insensitive login email.
     *
     * @param email login email
     * @return matching account, or an empty optional
     */
    Optional<User> findByEmailIgnoreCase(String email);
}
