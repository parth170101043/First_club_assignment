package com.example.FirstClubApp.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Groups validated request and response contracts used by mock payment APIs.
 */
public final class PaymentDtos {

    /**
     * Prevents instantiation of this DTO namespace.
     *
     * @return no instance
     * @implNote Used only by the Java runtime when enforcing the private constructor.
     */
    private PaymentDtos() {
    }

    /**
     * Defines tokenized metadata accepted when a user adds a payment method.
     */
    public record AddMethodRequest(
        @NotNull PaymentMethodType type,
        @NotBlank @Size(max = 255) String providerToken,
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 50) String brand,
        @Pattern(regexp = "[A-Za-z0-9]{4}") String lastFour,
        Boolean defaultMethod
    ) {
    }

    /**
     * Defines a safe payment method representation that excludes the provider token.
     */
    public record MethodResponse(
        UUID id,
        UUID userId,
        PaymentMethodType type,
        String displayName,
        String brand,
        String lastFour,
        boolean defaultMethod,
        boolean active,
        Instant createdAt
    ) {

        /**
         * Maps a persistent method to its safe API response.
         *
         * @param method initialized payment method entity
         * @return immutable response without sensitive provider token data
         * @implNote Used by {@link PaymentService} for method creation and listing.
         */
        static MethodResponse from(UserPaymentMethod method) {
            return new MethodResponse(
                method.getId(),
                method.getUser().getId(),
                method.getType(),
                method.getDisplayName(),
                method.getBrand(),
                method.getLastFour(),
                method.isDefaultMethod(),
                method.isActive(),
                method.getCreatedAt()
            );
        }
    }

    /**
     * Defines a synchronous mock charge request.
     */
    public record ChargeRequest(
        @NotNull UUID userId,
        @NotNull UUID paymentMethodId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotBlank @Size(max = 100) String purpose
    ) {
    }

    /**
     * Defines a persisted mock payment transaction response.
     */
    public record TransactionResponse(
        UUID id,
        UUID userId,
        UUID paymentMethodId,
        BigDecimal amount,
        String currency,
        String purpose,
        PaymentStatus status,
        String providerReference,
        String failureReason,
        Instant createdAt
    ) {

        /**
         * Maps a persistent transaction to its API response.
         *
         * @param transaction initialized payment transaction
         * @return immutable transaction response
         * @implNote Used by {@link PaymentService} for charges and history.
         */
        static TransactionResponse from(PaymentTransaction transaction) {
            return new TransactionResponse(
                transaction.getId(),
                transaction.getUser().getId(),
                transaction.getPaymentMethod().getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getPurpose(),
                transaction.getStatus(),
                transaction.getProviderReference(),
                transaction.getFailureReason(),
                transaction.getCreatedAt()
            );
        }
    }
}
