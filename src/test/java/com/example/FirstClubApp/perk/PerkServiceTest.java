package com.example.FirstClubApp.perk;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.subscription.Subscription;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.tier.Tier;
import com.example.FirstClubApp.tier.TierService;
import com.example.FirstClubApp.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies perk catalogue management, tier assignments, and user entitlement filtering.
 */
@ExtendWith(MockitoExtension.class)
class PerkServiceTest {

    @Mock
    private PerkRepository perkRepository;

    @Mock
    private TierPerkRepository tierPerkRepository;

    @Mock
    private TierService tierService;

    @Mock
    private SubscriptionService subscriptionService;

    private PerkService perkService;

    /**
     * Creates the service under test with mocked persistence and membership dependencies.
     *
     * @return no return value
     * @implNote Used by JUnit before each perk service test.
     */
    @BeforeEach
    void setUp() {
        perkService = new PerkService(
            perkRepository, tierPerkRepository, tierService, subscriptionService);
    }

    /**
     * Verifies perk creation normalizes the code and permits an unassigned catalogue entry.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect independent perk creation.
     */
    @Test
    void createsUnassignedPerk() {
        PerkDtos.CreateRequest request = new PerkDtos.CreateRequest(
            " extra_discount ", "Extra discount", "Discount", PerkType.PERCENTAGE_DISCOUNT,
            Map.of("discountPercent", 10));
        when(perkRepository.existsByCodeIgnoreCase("EXTRA_DISCOUNT")).thenReturn(false);
        when(perkRepository.save(org.mockito.ArgumentMatchers.any(Perk.class)))
            .thenAnswer(invocation -> initialize(invocation.getArgument(0), UUID.randomUUID()));

        PerkDtos.Response response = perkService.create(request);

        assertThat(response.code()).isEqualTo("EXTRA_DISCOUNT");
        assertThat(response.configuration()).containsEntry("discountPercent", 10);
        verify(tierPerkRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Verifies duplicate perk codes are rejected before persistence.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect perk-code uniqueness.
     */
    @Test
    void rejectsDuplicatePerkCode() {
        PerkDtos.CreateRequest request = new PerkDtos.CreateRequest(
            "EXTRA_DISCOUNT", "Extra discount", null, PerkType.PERCENTAGE_DISCOUNT, Map.of());
        when(perkRepository.existsByCodeIgnoreCase("EXTRA_DISCOUNT")).thenReturn(true);

        assertThatThrownBy(() -> perkService.create(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    /**
     * Verifies the complete perk catalogue includes active and inactive definitions.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect administrative catalogue listing.
     */
    @Test
    void listsCompletePerkCatalogue() {
        Perk active = initialize(activePerk(), UUID.randomUUID());
        Perk inactive = initialize(activePerk(), UUID.randomUUID());
        inactive.deactivate();
        when(perkRepository.findAllByOrderByNameAsc()).thenReturn(List.of(active, inactive));

        List<PerkDtos.Response> responses = perkService.findAll();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(PerkDtos.Response::active)
            .containsExactly(true, false);
    }

    /**
     * Verifies administrators can update perk configuration and activation state.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect reusable perk editing.
     */
    @Test
    void updatesPerkConfiguration() {
        UUID perkId = UUID.randomUUID();
        Perk perk = initialize(activePerk(), perkId);
        when(perkRepository.findById(perkId)).thenReturn(Optional.of(perk));
        PerkDtos.UpdateRequest request = new PerkDtos.UpdateRequest(
            "Bigger discount", "Updated", PerkType.PERCENTAGE_DISCOUNT,
            Map.of("discountPercent", 20), false);

        PerkDtos.Response response = perkService.update(perkId, request);

        assertThat(response.name()).isEqualTo("Bigger discount");
        assertThat(response.configuration()).containsEntry("discountPercent", 20);
        assertThat(response.active()).isFalse();
    }

    /**
     * Verifies administrative removal deletes an unassigned perk.
     *
     * @return no return value
     * @implNote Assigned perks are protected by a separate conflict check.
     */
    @Test
    void deletesUnassignedPerk() {
        UUID perkId = UUID.randomUUID();
        Perk perk = initialize(activePerk(), perkId);
        when(perkRepository.findById(perkId)).thenReturn(Optional.of(perk));

        when(tierPerkRepository.existsByPerkId(perkId)).thenReturn(false);

        perkService.deactivate(perkId);

        verify(perkRepository).delete(perk);
        verify(perkRepository).flush();
    }

    /**
     * Verifies assigning the same perk twice reuses the existing assignment.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect idempotent assignment updates.
     */
    @Test
    void reusesExistingTierAssignment() {
        UUID tierId = UUID.randomUUID();
        UUID perkId = UUID.randomUUID();
        Tier tier = initialize(tier(), tierId);
        Perk perk = initialize(activePerk(), perkId);
        TierPerk existing = initialize(new TierPerk(tier, perk), UUID.randomUUID());
        when(tierService.requireTier(tierId)).thenReturn(tier);
        when(perkRepository.findById(perkId)).thenReturn(Optional.of(perk));
        when(tierPerkRepository.findByTierIdAndPerkId(tierId, perkId))
            .thenReturn(Optional.of(existing));
        when(tierPerkRepository.save(existing)).thenReturn(existing);

        PerkDtos.AssignmentResponse response = perkService.assign(
            tierId, perkId, new PerkDtos.AssignmentRequest());

        assertThat(response.effectiveConfiguration()).containsEntry("discountPercent", 10);
        assertThat(response.assignmentId()).isEqualTo(existing.getId());
    }

    /**
     * Verifies a perk can be assigned to a tier using its base configuration.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect administrative assignment behavior.
     */
    @Test
    void assignsPerkUsingBaseConfiguration() {
        UUID tierId = UUID.randomUUID();
        UUID perkId = UUID.randomUUID();
        Tier tier = initialize(tier(), tierId);
        Perk perk = initialize(activePerk(), perkId);
        when(tierService.requireTier(tierId)).thenReturn(tier);
        when(perkRepository.findById(perkId)).thenReturn(Optional.of(perk));
        when(tierPerkRepository.findByTierIdAndPerkId(tierId, perkId))
            .thenReturn(Optional.empty());
        when(tierPerkRepository.save(org.mockito.ArgumentMatchers.any(TierPerk.class)))
            .thenAnswer(invocation -> initialize(invocation.getArgument(0), UUID.randomUUID()));

        PerkDtos.AssignmentResponse response = perkService.assign(
            tierId, perkId, new PerkDtos.AssignmentRequest());

        assertThat(response.effectiveConfiguration()).containsEntry("discountPercent", 10);
        assertThat(response.baseConfiguration()).containsEntry("discountPercent", 10);
    }

    /**
     * Verifies inactive perks cannot be assigned to a tier.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect perk activation rules.
     */
    @Test
    void rejectsInactivePerkAssignment() {
        UUID tierId = UUID.randomUUID();
        UUID perkId = UUID.randomUUID();
        Perk perk = activePerk();
        perk.deactivate();
        when(tierService.requireTier(tierId)).thenReturn(tier());
        when(perkRepository.findById(perkId)).thenReturn(Optional.of(perk));

        assertThatThrownBy(() -> perkService.assign(
            tierId, perkId, new PerkDtos.AssignmentRequest()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Inactive");
    }

    /**
     * Verifies unassignment deletes only the tier assignment and flushes the change.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect reusable perk retention.
     */
    @Test
    void removesTierAssignment() {
        UUID tierId = UUID.randomUUID();
        UUID perkId = UUID.randomUUID();
        TierPerk assignment = new TierPerk(tier(), activePerk());
        when(tierPerkRepository.findByTierIdAndPerkId(tierId, perkId))
            .thenReturn(Optional.of(assignment));

        perkService.unassign(tierId, perkId);

        verify(tierPerkRepository).delete(assignment);
        verify(tierPerkRepository).flush();
        verify(perkRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Verifies missing tier assignments produce a domain not-found error.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect repeated unassignment behavior.
     */
    @Test
    void rejectsMissingTierAssignment() {
        UUID tierId = UUID.randomUUID();
        UUID perkId = UUID.randomUUID();
        when(tierPerkRepository.findByTierIdAndPerkId(tierId, perkId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> perkService.unassign(tierId, perkId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("not assigned");
    }

    /**
     * Verifies user entitlements include active assigned perks and exclude inactive assignments.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect user-perk filtering.
     */
    @Test
    void returnsOnlyActivePerksForUsersSubscriptionTier() {
        UUID userId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        Subscription subscription = subscription(userId, tierId);
        Perk active = initialize(activePerk(), UUID.randomUUID());
        Perk inactive = initialize(activePerk(), UUID.randomUUID());
        inactive.deactivate();
        Tier tier = subscription.getTier();
        TierPerk activeAssignment = initialize(new TierPerk(tier, active), UUID.randomUUID());
        TierPerk inactiveAssignment =
            initialize(new TierPerk(tier, inactive), UUID.randomUUID());
        when(subscriptionService.requireActiveSubscription(userId)).thenReturn(subscription);
        when(tierPerkRepository.findAllByTierIdOrderByPerkNameAsc(tierId))
            .thenReturn(List.of(activeAssignment, inactiveAssignment));

        PerkDtos.UserPerksResponse response = perkService.getUserPerks(userId);

        assertThat(response.perks()).hasSize(1);
        assertThat(response.perks().getFirst().perkCode()).isEqualTo("EXTRA_DISCOUNT");
    }

    /**
     * Creates an active percentage-discount perk fixture.
     *
     * @return active perk with a base 10 percent configuration
     * @implNote Used internally by perk catalogue and assignment tests.
     */
    private Perk activePerk() {
        return new Perk("EXTRA_DISCOUNT", "Extra discount", "Discount",
            PerkType.PERCENTAGE_DISCOUNT, Map.of("discountPercent", 10));
    }

    /**
     * Creates an active Gold tier fixture.
     *
     * @return active tier with configured prices
     * @implNote Used internally by perk assignment and user entitlement tests.
     */
    private Tier tier() {
        return new Tier("GOLD", "Gold", "Gold tier", 2,
            new BigDecimal("299.00"), new BigDecimal("799.00"),
            new BigDecimal("2999.00"));
    }

    /**
     * Creates an initialized active subscription fixture.
     *
     * @param userId associated user UUID
     * @param tierId associated tier UUID
     * @return active monthly subscription fixture
     * @implNote Used internally by user entitlement tests.
     */
    private Subscription subscription(UUID userId, UUID tierId) {
        User user = initialize(new User("member@example.com", "Member", "User", null), userId);
        Tier tier = initialize(tier(), tierId);
        Subscription subscription = new Subscription(
            user, tier, BillingCycle.MONTHLY, Instant.parse("2026-06-21T06:30:00Z"),
            new BigDecimal("299.00"), "INR");
        return initialize(subscription, UUID.randomUUID());
    }
}
