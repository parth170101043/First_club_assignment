package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Stores the administrator-configurable thresholds for behavioral membership tiers.
 */
@Entity
@Table(name = "behavioral_tier_settings")
public class BehavioralTierSettings extends AuditableEntity {

    @Column(name = "gold_order_count", nullable = false)
    private long goldOrderCount;

    @Column(name = "platinum_order_count", nullable = false)
    private long platinumOrderCount;

    @Column(name = "gold_monthly_spend", nullable = false, precision = 12, scale = 2)
    private BigDecimal goldMonthlySpend;

    @Column(name = "platinum_monthly_spend", nullable = false, precision = 12, scale = 2)
    private BigDecimal platinumMonthlySpend;

    @Column(name = "gold_cohort", nullable = false, length = 100)
    private String goldCohort;

    @Column(name = "platinum_cohort", nullable = false, length = 100)
    private String platinumCohort;

    protected BehavioralTierSettings() {
    }

    public void update(long goldOrderCount,
                       long platinumOrderCount,
                       BigDecimal goldMonthlySpend,
                       BigDecimal platinumMonthlySpend,
                       String goldCohort,
                       String platinumCohort) {
        this.goldOrderCount = goldOrderCount;
        this.platinumOrderCount = platinumOrderCount;
        this.goldMonthlySpend = goldMonthlySpend;
        this.platinumMonthlySpend = platinumMonthlySpend;
        this.goldCohort = goldCohort;
        this.platinumCohort = platinumCohort;
    }

    public long getGoldOrderCount() {
        return goldOrderCount;
    }

    public long getPlatinumOrderCount() {
        return platinumOrderCount;
    }

    public BigDecimal getGoldMonthlySpend() {
        return goldMonthlySpend;
    }

    public BigDecimal getPlatinumMonthlySpend() {
        return platinumMonthlySpend;
    }

    public String getGoldCohort() {
        return goldCohort;
    }

    public String getPlatinumCohort() {
        return platinumCohort;
    }
}
