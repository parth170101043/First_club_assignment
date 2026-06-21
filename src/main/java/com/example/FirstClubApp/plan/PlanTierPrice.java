package com.example.FirstClubApp.plan;

import com.example.FirstClubApp.common.AuditableEntity;
import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.tier.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Stores configurable pricing for one billing cycle and paid tier combination.
 */
@Entity
@Table(name = "plan_tier_prices")
public class PlanTierPrice extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    private Tier tier;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Required by JPA when materializing pricing from PostgreSQL.
     *
     * @return pricing with fields left unset for JPA population
     * @implNote Used only by the JPA persistence provider.
     */
    protected PlanTierPrice() {
    }

    /**
     * Creates active pricing for a plan and tier.
     *
     * @param billingCycle subscription duration
     * @param tier paid minimum tier
     * @param price non-negative configured price
     * @param currency three-letter currency code
     * @return a new active pricing record
     * @implNote Used by future administrative pricing creation.
     */
    public PlanTierPrice(BillingCycle billingCycle, Tier tier,
                         BigDecimal price, String currency) {
        this.billingCycle = billingCycle;
        this.tier = tier;
        this.price = price;
        this.currency = currency;
    }

    /**
     * Replaces the configured price and currency.
     *
     * @param price non-negative replacement price
     * @param currency three-letter replacement currency
     * @return no return value
     * @implNote Used by plan-tier pricing administration.
     */
    public void update(BigDecimal price, String currency) {
        this.price = price;
        this.currency = currency;
    }

    /**
     * Returns the duration billing cycle.
     *
     * @return Monthly, Quarterly, or Yearly
     * @implNote Used by catalogue and pricing responses.
     */
    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    /**
     * Returns the paid membership tier.
     *
     * @return associated tier
     * @implNote Used by catalogue and pricing responses.
     */
    public Tier getTier() {
        return tier;
    }

    /**
     * Returns the configured price.
     *
     * @return plan-tier price
     * @implNote Used by subscription creation, upgrades, renewals, and responses.
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Returns the configured currency.
     *
     * @return three-letter currency code
     * @implNote Used by subscription and pricing responses.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Reports whether this combination can be purchased.
     *
     * @return {@code true} for active pricing
     * @implNote Used by subscription validation.
     */
    public boolean isActive() {
        return active;
    }
}
