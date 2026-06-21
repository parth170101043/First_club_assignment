package com.example.FirstClubApp.payment;

import com.example.FirstClubApp.common.AuditableEntity;
import com.example.FirstClubApp.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Maps a user to tokenized mock payment method metadata without storing sensitive credentials.
 */
@Entity
@Table(name = "user_payment_methods")
public class UserPaymentMethod extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 20)
    private PaymentMethodType type;

    @Column(name = "provider_token", nullable = false, unique = true)
    private String providerToken;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 50)
    private String brand;

    @Column(name = "last_four", length = 4)
    private String lastFour;

    @Column(name = "default_method", nullable = false)
    private boolean defaultMethod;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Required by JPA when materializing a payment method from PostgreSQL.
     *
     * @return a payment method with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected UserPaymentMethod() {
    }

    /**
     * Creates an active tokenized payment method.
     *
     * @param user owner of the payment method
     * @param type payment method type
     * @param providerToken mock provider token; raw card numbers and CVVs are not accepted
     * @param displayName user-facing payment method name
     * @param brand optional card or payment brand; defaults to {@code null}
     * @param lastFour optional masked suffix; defaults to {@code null}
     * @param defaultMethod whether this is the user's default method
     * @return a new active payment method
     * @implNote Used by {@link PaymentService} when a payment method is added.
     */
    public UserPaymentMethod(User user,
                             PaymentMethodType type,
                             String providerToken,
                             String displayName,
                             String brand,
                             String lastFour,
                             boolean defaultMethod) {
        this.user = user;
        this.type = type;
        this.providerToken = providerToken;
        this.displayName = displayName;
        this.brand = brand;
        this.lastFour = lastFour;
        this.defaultMethod = defaultMethod;
    }

    /**
     * Marks the payment method as the default.
     *
     * @return no return value
     * @implNote Used by {@link PaymentService} while maintaining one default method per user.
     */
    public void makeDefault() {
        this.defaultMethod = true;
    }

    /**
     * Removes default status from the payment method.
     *
     * @return no return value
     * @implNote Used by {@link PaymentService} before selecting another default method.
     */
    public void removeDefault() {
        this.defaultMethod = false;
    }

    /**
     * Deactivates the payment method without deleting transaction history.
     *
     * @return no return value
     * @implNote Used by {@link PaymentService} for payment method removal.
     */
    public void deactivate() {
        this.active = false;
        this.defaultMethod = false;
    }

    /**
     * Returns the payment method owner.
     *
     * @return associated user
     * @implNote Used by ownership checks and response mapping.
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns the payment method type.
     *
     * @return configured type
     * @implNote Used by API response mapping.
     */
    public PaymentMethodType getType() {
        return type;
    }

    /**
     * Returns the mock provider token.
     *
     * @return token used by the mock processor
     * @implNote Used only by {@link MockPaymentProcessor}; it is never returned by the API.
     */
    public String getProviderToken() {
        return providerToken;
    }

    /**
     * Returns the user-facing payment method name.
     *
     * @return display name
     * @implNote Used by API response mapping.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the optional payment brand.
     *
     * @return brand, or {@code null} when omitted
     * @implNote Used by API response mapping.
     */
    public String getBrand() {
        return brand;
    }

    /**
     * Returns the optional masked suffix.
     *
     * @return last four characters, or {@code null} when omitted
     * @implNote Used by API response mapping.
     */
    public String getLastFour() {
        return lastFour;
    }

    /**
     * Reports whether this is the default active payment method.
     *
     * @return {@code true} when selected as default
     * @implNote Used by payment selection and API response mapping.
     */
    public boolean isDefaultMethod() {
        return defaultMethod;
    }

    /**
     * Reports whether the payment method can be charged.
     *
     * @return {@code true} by default until the method is deactivated
     * @implNote Used by payment charge validation and API response mapping.
     */
    public boolean isActive() {
        return active;
    }
}
