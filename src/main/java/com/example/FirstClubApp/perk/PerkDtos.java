package com.example.FirstClubApp.perk;

import com.example.FirstClubApp.subscription.Subscription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Groups validated requests and responses used by perk administration and user-perk APIs.
 */
public final class PerkDtos {

    /**
     * Prevents instantiation of this DTO namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime when enforcing the private constructor.
     */
    private PerkDtos() {
    }

    /**
     * Defines validated fields accepted when creating a reusable perk.
     */
    public record CreateRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]+") @Size(max = 80) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull PerkType type,
        Map<String, Object> configuration
    ) {
    }

    /**
     * Defines mutable fields accepted when updating a reusable perk.
     */
    public record UpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull PerkType type,
        Map<String, Object> configuration,
        boolean active
    ) {
    }

    /**
     * Defines an assignment request. Perk configuration is owned by the perk itself.
     */
    public record AssignmentRequest() {
    }

    /**
     * Defines the administrative representation of a reusable perk.
     */
    public record Response(
        UUID id,
        String code,
        String name,
        String description,
        PerkType type,
        Map<String, Object> configuration,
        boolean active,
        long version
    ) {
        /**
         * Maps a persistent perk to its administrative response.
         *
         * @param perk persistent perk definition
         * @return immutable perk response
         * @implNote Used by {@link PerkService} for catalogue operations.
         */
        static Response from(Perk perk) {
            return new Response(perk.getId(), perk.getCode(), perk.getName(),
                perk.getDescription(), perk.getType(), perk.getConfiguration(),
                perk.isActive(), perk.getVersion());
        }
    }

    /**
     * Defines one tier assignment and its effective configuration.
     */
    public record AssignmentResponse(
        UUID assignmentId,
        UUID tierId,
        UUID perkId,
        String perkCode,
        String perkName,
        String description,
        PerkType type,
        Map<String, Object> baseConfiguration,
        Map<String, Object> effectiveConfiguration,
        boolean active
    ) {
        /**
         * Maps a tier-perk assignment to its API representation.
         *
         * @param assignment persistent tier-perk assignment
         * @return immutable assignment response
         * @implNote Used by admin assignment and user-perk response builders.
         */
        static AssignmentResponse from(TierPerk assignment) {
            Perk perk = assignment.getPerk();
            return new AssignmentResponse(
                assignment.getId(),
                assignment.getTier().getId(),
                perk.getId(),
                perk.getCode(),
                perk.getName(),
                perk.getDescription(),
                perk.getType(),
                perk.getConfiguration(),
                assignment.getEffectiveConfiguration(),
                perk.isActive()
            );
        }
    }

    /**
     * Defines perks available to a user through the user's active subscription.
     */
    public record UserPerksResponse(
        UUID userId,
        UUID subscriptionId,
        UUID tierId,
        String tierCode,
        Instant subscriptionExpiresAt,
        List<AssignmentResponse> perks
    ) {
        /**
         * Builds a user-perks response from an active subscription and filtered assignments.
         *
         * @param subscription active subscription used to identify the user's tier
         * @param perks active assignments available through that tier; defaults to an empty list
         * @return immutable user-perks response
         * @implNote Used by {@link PerkService#getUserPerks(UUID)}.
         */
        static UserPerksResponse from(Subscription subscription,
                                      List<AssignmentResponse> perks) {
            return new UserPerksResponse(
                subscription.getUser().getId(),
                subscription.getId(),
                subscription.getTier().getId(),
                subscription.getTier().getCode(),
                subscription.getExpiresAt(),
                perks
            );
        }
    }
}
