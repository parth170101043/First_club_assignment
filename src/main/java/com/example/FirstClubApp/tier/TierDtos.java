package com.example.FirstClubApp.tier;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Groups validated request and response contracts used by the tier REST API.
 */
public final class TierDtos {

    /**
     * Prevents instantiation of this DTO namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime when enforcing the private constructor.
     */
    private TierDtos() {
    }

    /**
     * Defines validated fields accepted when creating a tier.
     */
    public record CreateRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]+") @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Min(1) int rank,
        @NotNull @DecimalMin("0.00") BigDecimal monthlyPrice,
        @NotNull @DecimalMin("0.00") BigDecimal quarterlyPrice,
        @NotNull @DecimalMin("0.00") BigDecimal yearlyPrice
    ) {
    }

    /**
     * Defines mutable tier fields accepted by the update API.
     */
    public record UpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("0.00") BigDecimal monthlyPrice,
        @NotNull @DecimalMin("0.00") BigDecimal quarterlyPrice,
        @NotNull @DecimalMin("0.00") BigDecimal yearlyPrice,
        boolean active
    ) {
    }

    /**
     * Defines the three subscription prices accepted by the admin pricing API.
     */
    public record PriceUpdateRequest(
        @NotNull @DecimalMin("0.00") BigDecimal monthlyPrice,
        @NotNull @DecimalMin("0.00") BigDecimal quarterlyPrice,
        @NotNull @DecimalMin("0.00") BigDecimal yearlyPrice
    ) {
    }

    /**
     * Defines the public tier catalogue representation.
     */
    public record Response(
        UUID id,
        String code,
        String name,
        String description,
        int rank,
        BigDecimal monthlyPrice,
        BigDecimal quarterlyPrice,
        BigDecimal yearlyPrice,
        boolean active
    ) {
        /**
         * Maps a persistent tier to its public response.
         *
         * @param tier persisted tier entity
         * @return immutable tier response
         * @implNote Used by {@link TierService} for create, update, and query operations.
         */
        public static Response from(Tier tier) {
            return new Response(tier.getId(), tier.getCode(), tier.getName(),
                tier.getDescription(), tier.getRank(), tier.getMonthlyPrice(),
                tier.getQuarterlyPrice(), tier.getYearlyPrice(), tier.isActive());
        }
    }
}
