package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.common.AuditableEntity;
import com.example.FirstClubApp.subscription.BillingCycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Represents a configurable membership tier and its prices for each billing cycle.
 */
@Entity
@Table(name = "tiers")
public class Tier extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "tier_rank", nullable = false, unique = true)
    private int rank;

    @Column(name = "monthly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "quarterly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal quarterlyPrice;

    @Column(name = "yearly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal yearlyPrice;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Required by JPA when materializing a tier from the database.
     *
     * @return a tier with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected Tier() {
    }

    /**
     * Creates an active membership tier.
     *
     * @param code unique machine-readable tier code
     * @param name display name
     * @param description optional description; defaults to {@code null}
     * @param rank positive ordering rank
     * @param monthlyPrice price for one calendar month
     * @param quarterlyPrice price for three calendar months
     * @param yearlyPrice price for twelve calendar months
     * @return a new active tier entity
     * @implNote Used by {@link TierService} for administrative tier creation.
     */
    public Tier(String code, String name, String description, int rank,
                BigDecimal monthlyPrice, BigDecimal quarterlyPrice, BigDecimal yearlyPrice) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.rank = rank;
        this.monthlyPrice = monthlyPrice;
        this.quarterlyPrice = quarterlyPrice;
        this.yearlyPrice = yearlyPrice;
    }

    /**
     * Updates mutable tier details while preserving its code and rank.
     *
     * @param name updated display name
     * @param description updated optional description; may be {@code null}
     * @param monthlyPrice updated monthly price
     * @param quarterlyPrice updated quarterly price
     * @param yearlyPrice updated yearly price
     * @param active whether users may select this tier
     * @return no return value
     * @implNote Used by {@link TierService} for administrative tier updates.
     */
    public void update(String name, String description, BigDecimal monthlyPrice,
                       BigDecimal quarterlyPrice, BigDecimal yearlyPrice, boolean active) {
        this.name = name;
        this.description = description;
        this.monthlyPrice = monthlyPrice;
        this.quarterlyPrice = quarterlyPrice;
        this.yearlyPrice = yearlyPrice;
        this.active = active;
    }

    /**
     * Updates subscription prices without changing tier identity, description, rank, or status.
     *
     * @param monthlyPrice updated price for one calendar month
     * @param quarterlyPrice updated price for three calendar months
     * @param yearlyPrice updated price for twelve calendar months
     * @return no return value
     * @implNote Used by {@link TierService} for focused administrative price changes.
     */
    public void updatePrices(BigDecimal monthlyPrice,
                             BigDecimal quarterlyPrice,
                             BigDecimal yearlyPrice) {
        this.monthlyPrice = monthlyPrice;
        this.quarterlyPrice = quarterlyPrice;
        this.yearlyPrice = yearlyPrice;
    }

    /**
     * Selects the tier price for a billing cycle.
     *
     * @param billingCycle requested billing cycle with no default value
     * @return configured price for the requested cycle
     * @implNote Used by {@code SubscriptionService} when creating a subscription price snapshot.
     */
    public BigDecimal priceFor(BillingCycle billingCycle) {
        return switch (billingCycle) {
            case MONTHLY -> monthlyPrice;
            case QUARTERLY -> quarterlyPrice;
            case YEARLY -> yearlyPrice;
        };
    }

    /**
     * Returns the machine-readable tier code.
     *
     * @return unique tier code
     * @implNote Used by API response mappers.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the tier display name.
     *
     * @return tier name
     * @implNote Used by API response mappers.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the optional tier description.
     *
     * @return description, or {@code null} when absent
     * @implNote Used by API response mappers.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the tier ordering rank.
     *
     * @return positive rank where a larger value represents a higher tier
     * @implNote Used by tier ordering and API response mappers.
     */
    public int getRank() {
        return rank;
    }

    /**
     * Returns the monthly price.
     *
     * @return monthly price
     * @implNote Used by tier and subscription services.
     */
    public BigDecimal getMonthlyPrice() {
        return monthlyPrice;
    }

    /**
     * Returns the quarterly price.
     *
     * @return quarterly price
     * @implNote Used by tier and subscription services.
     */
    public BigDecimal getQuarterlyPrice() {
        return quarterlyPrice;
    }

    /**
     * Returns the yearly price.
     *
     * @return yearly price
     * @implNote Used by tier and subscription services.
     */
    public BigDecimal getYearlyPrice() {
        return yearlyPrice;
    }

    /**
     * Reports whether users may select the tier.
     *
     * @return {@code true} by default for newly created tiers
     * @implNote Used by tier queries and subscription validation.
     */
    public boolean isActive() {
        return active;
    }
}
