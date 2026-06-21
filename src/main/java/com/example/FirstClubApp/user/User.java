package com.example.FirstClubApp.user;

import com.example.FirstClubApp.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Stores the profile and eligibility state of a FirstClub member.
 */
@Entity
@Table(name = "app_users")
public class User extends AuditableEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 100)
    private String cohort;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.MEMBER;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Required by JPA when materializing a user from the database.
     *
     * @return a user with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected User() {
    }

    /**
     * Creates an enabled member profile.
     *
     * @param email normalized unique email address
     * @param firstName member's first name
     * @param lastName member's last name
     * @param cohort optional cohort name; defaults to {@code null}
     * @return a new enabled user entity
     * @implNote Used by {@link UserService} when the user creation API is called.
     */
    public User(String email, String firstName, String lastName, String cohort) {
        this(email, firstName, lastName, cohort, "LOGIN_NOT_CONFIGURED", UserRole.MEMBER);
    }

    /**
     * Creates an enabled account with login credentials and an authorization role.
     *
     * @param email normalized unique email address
     * @param firstName member's first name
     * @param lastName member's last name
     * @param cohort optional cohort name
     * @param passwordHash one-way encoded password
     * @param role account authorization role
     * @return a new enabled user account
     * @implNote Used by signup and administrative account creation.
     */
    public User(String email, String firstName, String lastName, String cohort,
                String passwordHash, UserRole role) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.cohort = cohort;
        this.passwordHash = passwordHash;
        this.role = role == null ? UserRole.MEMBER : role;
    }

    /**
     * Returns the member's unique email address.
     *
     * @return normalized email address
     * @implNote Used by response mappers and future authentication services.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the member's first name.
     *
     * @return first name
     * @implNote Used by user response mappers.
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Returns the member's last name.
     *
     * @return last name
     * @implNote Used by user response mappers.
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Returns the optional membership cohort.
     *
     * @return cohort name, or {@code null} when no cohort was assigned
     * @implNote Used by response mappers and future automatic tier rules.
     */
    public String getCohort() {
        return cohort;
    }

    /**
     * Reports whether the member can perform membership actions.
     *
     * @return {@code true} by default for newly created users
     * @implNote Used by {@code SubscriptionService} before creating a subscription.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the one-way password hash used by Spring Security.
     *
     * @return encoded password
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Returns the account authorization role.
     *
     * @return MEMBER or ADMIN
     */
    public UserRole getRole() {
        return role;
    }
}
