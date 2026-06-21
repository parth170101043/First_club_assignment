package com.example.FirstClubApp.tier;

import com.example.FirstClubApp.common.ConflictException;
import com.example.FirstClubApp.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.FirstClubApp.testutil.TestEntityFactory.initialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies tier uniqueness, pricing updates, activation checks, and missing-tier behavior.
 */
@ExtendWith(MockitoExtension.class)
class TierServiceTest {

    @Mock
    private TierRepository tierRepository;

    private TierService tierService;

    /**
     * Creates the service under test with a mocked tier repository.
     *
     * @return no return value
     * @implNote Used by JUnit before each tier service test.
     */
    @BeforeEach
    void setUp() {
        tierService = new TierService(tierRepository);
    }

    /**
     * Verifies that tier creation normalizes its code and persists all prices.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect tier creation behavior.
     */
    @Test
    void createsNormalizedTier() {
        TierDtos.CreateRequest request = createRequest(" diamond ");
        when(tierRepository.existsByCodeIgnoreCase("DIAMOND")).thenReturn(false);
        when(tierRepository.existsByRank(4)).thenReturn(false);
        when(tierRepository.save(org.mockito.ArgumentMatchers.any(Tier.class)))
            .thenAnswer(invocation -> initialize(invocation.getArgument(0), UUID.randomUUID()));

        TierDtos.Response response = tierService.create(request);

        assertThat(response.code()).isEqualTo("DIAMOND");
        assertThat(response.monthlyPrice()).isEqualByComparingTo("699.00");
    }

    /**
     * Verifies that duplicate tier codes are rejected before persistence.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect tier-code uniqueness.
     */
    @Test
    void rejectsDuplicateTierCode() {
        TierDtos.CreateRequest request = createRequest("DIAMOND");
        when(tierRepository.existsByCodeIgnoreCase("DIAMOND")).thenReturn(true);

        assertThatThrownBy(() -> tierService.create(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("code");
        verify(tierRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Verifies that duplicate tier ranks are rejected before persistence.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect tier ordering uniqueness.
     */
    @Test
    void rejectsDuplicateTierRank() {
        TierDtos.CreateRequest request = createRequest("DIAMOND");
        when(tierRepository.existsByCodeIgnoreCase("DIAMOND")).thenReturn(false);
        when(tierRepository.existsByRank(4)).thenReturn(true);

        assertThatThrownBy(() -> tierService.create(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("rank");
    }

    /**
     * Verifies that active tiers are mapped in repository rank order.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect the public tier catalogue.
     */
    @Test
    void listsActiveTiers() {
        Tier silver = initialize(new Tier("SILVER", "Silver", "Entry", 1,
            new BigDecimal("199.00"), new BigDecimal("549.00"),
            new BigDecimal("1999.00")), UUID.randomUUID());
        Tier gold = initialize(tier("GOLD"), UUID.randomUUID());
        when(tierRepository.findAllByActiveTrueOrderByRankAsc())
            .thenReturn(List.of(silver, gold));

        List<TierDtos.Response> responses = tierService.findActive();

        assertThat(responses).extracting(TierDtos.Response::code)
            .containsExactly("SILVER", "GOLD");
    }

    /**
     * Verifies that mutable tier prices and activation state can be updated.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect administrative tier editing.
     */
    @Test
    void updatesTierConfiguration() {
        UUID tierId = UUID.randomUUID();
        Tier tier = initialize(tier("GOLD"), tierId);
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        TierDtos.UpdateRequest request = new TierDtos.UpdateRequest(
            "Gold Plus", "Updated", new BigDecimal("349.00"),
            new BigDecimal("899.00"), new BigDecimal("3299.00"), false);

        TierDtos.Response response = tierService.update(tierId, request);

        assertThat(response.name()).isEqualTo("Gold Plus");
        assertThat(response.monthlyPrice()).isEqualByComparingTo("349.00");
        assertThat(response.active()).isFalse();
    }

    /**
     * Verifies the admin pricing operation changes only billing prices.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect focused subscription price updates.
     */
    @Test
    void updatesSubscriptionPrices() {
        UUID tierId = UUID.randomUUID();
        Tier tier = initialize(tier("GOLD"), tierId);
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));
        TierDtos.PriceUpdateRequest request = new TierDtos.PriceUpdateRequest(
            new BigDecimal("399.00"),
            new BigDecimal("999.00"),
            new BigDecimal("3599.00")
        );

        TierDtos.Response response = tierService.updatePrices(tierId, request);

        assertThat(response.name()).isEqualTo("Diamond");
        assertThat(response.active()).isTrue();
        assertThat(response.monthlyPrice()).isEqualByComparingTo("399.00");
        assertThat(response.quarterlyPrice()).isEqualByComparingTo("999.00");
        assertThat(response.yearlyPrice()).isEqualByComparingTo("3599.00");
    }

    /**
     * Verifies that inactive tiers cannot be selected for subscriptions.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect subscription tier validation.
     */
    @Test
    void rejectsInactiveTierSelection() {
        UUID tierId = UUID.randomUUID();
        Tier tier = tier("DIAMOND");
        tier.update("Diamond", "Premium", new BigDecimal("699.00"),
            new BigDecimal("1899.00"), new BigDecimal("6999.00"), false);
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        assertThatThrownBy(() -> tierService.requireActiveTier(tierId))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("inactive");
    }

    /**
     * Verifies that missing tiers produce a domain not-found error.
     *
     * @return no return value
     * @implNote Used by Maven's test lifecycle to protect missing-tier API behavior.
     */
    @Test
    void rejectsMissingTierLookup() {
        UUID tierId = UUID.randomUUID();
        when(tierRepository.findById(tierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tierService.findById(tierId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(tierId.toString());
    }

    /**
     * Creates a valid tier creation request.
     *
     * @param code tier code supplied by the individual test
     * @return validated request fixture with rank {@code 4}
     * @implNote Used internally by tier creation tests.
     */
    private TierDtos.CreateRequest createRequest(String code) {
        return new TierDtos.CreateRequest(code, "Diamond", "Premium tier", 4,
            new BigDecimal("699.00"), new BigDecimal("1899.00"),
            new BigDecimal("6999.00"));
    }

    /**
     * Creates an active in-memory tier fixture.
     *
     * @param code machine-readable tier code
     * @return active tier fixture
     * @implNote Used internally by tier activation tests.
     */
    private Tier tier(String code) {
        return new Tier(code, "Diamond", "Premium tier", 4,
            new BigDecimal("699.00"), new BigDecimal("1899.00"),
            new BigDecimal("6999.00"));
    }
}
