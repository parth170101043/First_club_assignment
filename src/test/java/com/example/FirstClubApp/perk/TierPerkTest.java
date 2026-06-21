package com.example.FirstClubApp.perk;

import com.example.FirstClubApp.tier.Tier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that assignments use their reusable perk configuration.
 */
class TierPerkTest {

    /**
     * Verifies that an assignment exposes the reusable perk configuration.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect default configuration behavior.
     */
    @Test
    void usesBaseConfigurationWhenOverrideIsAbsent() {
        TierPerk assignment = assignment();

        assertThat(assignment.getEffectiveConfiguration())
            .containsEntry("discountPercent", 10);
    }

    /**
     * Creates a test assignment.
     *
     * @return initialized in-memory tier-perk assignment
     * @implNote Used internally by the configuration-resolution test.
     */
    private TierPerk assignment() {
        Tier tier = new Tier("GOLD", "Gold", "Gold tier", 2,
            new BigDecimal("299.00"), new BigDecimal("799.00"),
            new BigDecimal("2999.00"));
        Perk perk = new Perk("EXTRA_DISCOUNT", "Extra discount", "Order discount",
            PerkType.PERCENTAGE_DISCOUNT, Map.of("discountPercent", 10));
        return new TierPerk(tier, perk);
    }
}
