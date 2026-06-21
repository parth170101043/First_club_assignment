package com.example.FirstClubApp.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Groups request and response contracts used by the user REST API.
 */
public final class UserDtos {

    /**
     * Prevents instantiation of this DTO namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime when enforcing the private constructor.
     */
    private UserDtos() {
    }

    /**
     * Defines validated fields accepted when creating a user.
     */
    public record CreateRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String cohort,
        @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    /**
     * Defines the public user representation returned by the API.
     */
    public record Response(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String cohort,
        boolean enabled,
        Instant createdAt
    ) {
        /**
         * Maps a persistent user to its public response.
         *
         * @param user persisted user entity
         * @return immutable user response
         * @implNote Used by {@link UserService} for create and query operations.
         */
        static Response from(User user) {
            return new Response(user.getId(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getCohort(), user.isEnabled(), user.getCreatedAt());
        }
    }
}
