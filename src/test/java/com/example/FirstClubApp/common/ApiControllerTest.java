package com.example.FirstClubApp.common;

import com.example.FirstClubApp.admin.AdminPerkController;
import com.example.FirstClubApp.admin.AdminTierController;
import com.example.FirstClubApp.admin.AdminTierPerkController;
import com.example.FirstClubApp.discount.DiscountDtos;
import com.example.FirstClubApp.discount.DiscountService;
import com.example.FirstClubApp.discount.UserDiscountController;
import com.example.FirstClubApp.perk.PerkDtos;
import com.example.FirstClubApp.perk.PerkService;
import com.example.FirstClubApp.perk.PerkType;
import com.example.FirstClubApp.perk.UserPerkController;
import com.example.FirstClubApp.plan.MembershipPlanController;
import com.example.FirstClubApp.plan.MembershipPlanService;
import com.example.FirstClubApp.plan.PlanDtos;
import com.example.FirstClubApp.subscription.BillingCycle;
import com.example.FirstClubApp.subscription.SubscriptionController;
import com.example.FirstClubApp.subscription.SubscriptionDtos;
import com.example.FirstClubApp.subscription.SubscriptionService;
import com.example.FirstClubApp.subscription.SubscriptionStatus;
import com.example.FirstClubApp.tier.TierController;
import com.example.FirstClubApp.tier.TierDtos;
import com.example.FirstClubApp.tier.TierService;
import com.example.FirstClubApp.user.UserController;
import com.example.FirstClubApp.user.UserDtos;
import com.example.FirstClubApp.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies REST routes, JSON contracts, validation, status codes, and exception translation.
 */
@ExtendWith(MockitoExtension.class)
class ApiControllerTest {

    private static final UUID USER_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TIER_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PERK_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SUBSCRIPTION_ID =
        UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PAYMENT_METHOD_ID =
        UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID ASSIGNMENT_ID =
        UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant START = Instant.parse("2026-06-21T06:30:00Z");
    private static final Instant EXPIRY = Instant.parse("2026-07-21T06:30:00Z");

    @Mock
    private UserService userService;

    @Mock
    private TierService tierService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private PerkService perkService;

    @Mock
    private DiscountService discountService;

    @Mock
    private MembershipPlanService planService;

    private MockMvc mockMvc;

    /**
     * Creates a standalone MVC environment containing every current REST controller.
     *
     * @return no return value
     * @implNote Used by JUnit before each HTTP contract test without starting PostgreSQL.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new UserController(userService),
                new TierController(tierService),
                new SubscriptionController(subscriptionService),
                new MembershipPlanController(planService),
                new AdminPerkController(perkService),
                new AdminTierController(tierService, planService),
                new AdminTierPerkController(perkService),
                new UserPerkController(perkService),
                new UserDiscountController(discountService)
            )
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    /**
     * Verifies valid user JSON creates a user and returns HTTP 201.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect the user creation endpoint.
     */
    @Test
    void createsUserThroughRestApi() throws Exception {
        when(userService.create(any(UserDtos.CreateRequest.class))).thenReturn(userResponse());

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "member@example.com",
                      "firstName": "Member",
                      "lastName": "User",
                      "password": "password123"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.email").value("member@example.com"));
    }

    /**
     * Verifies invalid user JSON returns HTTP 400 with field-level validation details.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect request validation and error formatting.
     */
    @Test
    void rejectsInvalidUserRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email",
                      "firstName": "",
                      "lastName": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.errors.email").exists())
            .andExpect(jsonPath("$.errors.firstName").exists());
    }

    /**
     * Verifies user deletion delegates to the service and returns HTTP 204.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect test-user cleanup routing.
     */
    @Test
    void deletesUserThroughRestApi() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{id}", USER_ID))
            .andExpect(status().isNoContent());

        verify(userService).delete(USER_ID);
    }

    /**
     * Verifies a missing user is translated into an HTTP 404 problem response.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect domain exception translation.
     */
    @Test
    void returnsNotFoundForMissingUser() throws Exception {
        when(userService.findById(USER_ID))
            .thenThrow(new ResourceNotFoundException("User not found: " + USER_ID));

        mockMvc.perform(get("/api/v1/users/{id}", USER_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    /**
     * Verifies protected user deletion conflicts are translated into HTTP 409.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect subscription-history safeguards.
     */
    @Test
    void returnsConflictWhenUserCannotBeDeleted() throws Exception {
        doThrow(new ConflictException("Users with subscription history cannot be deleted."))
            .when(userService).delete(USER_ID);

        mockMvc.perform(delete("/api/v1/users/{id}", USER_ID))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Request conflict"));
    }

    /**
     * Verifies stale concurrent writes are translated into a retryable HTTP 409 response.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect concurrent membership update handling.
     */
    @Test
    void returnsConflictForConcurrentSubscriptionUpdate() throws Exception {
        when(subscriptionService.currentForUser(USER_ID))
            .thenThrow(new ObjectOptimisticLockingFailureException(
                "Subscription", SUBSCRIPTION_ID));

        mockMvc.perform(get(
                "/api/v1/subscriptions/users/{userId}/current", USER_ID))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title")
                .value("Concurrent update conflict"));
    }

    /**
     * Verifies the tier catalogue endpoint returns configured tier prices.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect membership selection responses.
     */
    @Test
    void listsTiersThroughRestApi() throws Exception {
        when(tierService.findActive()).thenReturn(List.of(tierResponse()));

        mockMvc.perform(get("/api/v1/tiers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("GOLD"))
            .andExpect(jsonPath("$[0].monthlyPrice").value(299.00));
    }

    /**
     * Verifies administrators can replace all subscription prices through the focused endpoint.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect administrative tier pricing.
     */
    // @Test
    // void updatesTierPricesThroughAdminRestApi() throws Exception {
    //     TierDtos.Response updated = new TierDtos.Response(
    //         TIER_ID, "GOLD", "Gold", "Gold tier", 2,
    //         new BigDecimal("399.00"), new BigDecimal("999.00"),
    //         new BigDecimal("3599.00"), true);
    //     when(tierService.updatePrices(eq(TIER_ID),
    //         any(TierDtos.PriceUpdateRequest.class))).thenReturn(updated);

    //     mockMvc.perform(patch("/api/v1/admin/tiers/{tierId}/prices", TIER_ID)
    //             .contentType(MediaType.APPLICATION_JSON)
    //             .content("""
    //                 {
    //                   "monthlyPrice": 399.00,
    //                   "quarterlyPrice": 999.00,
    //                   "yearlyPrice": 3599.00
    //                 }
    //                 """))
    //         .andExpect(status().isOk())
    //         .andExpect(jsonPath("$.monthlyPrice").value(399.00))
    //         .andExpect(jsonPath("$.quarterlyPrice").value(999.00))
    //         .andExpect(jsonPath("$.yearlyPrice").value(3599.00));
    // }

    /**
     * Verifies negative admin prices are rejected by request validation.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect non-negative subscription pricing.
     */
    // @Test
    // void rejectsNegativeTierPriceThroughAdminRestApi() throws Exception {
    //     mockMvc.perform(patch("/api/v1/admin/tiers/{tierId}/prices", TIER_ID)
    //             .contentType(MediaType.APPLICATION_JSON)
    //             .content("""
    //                 {
    //                   "monthlyPrice": -1.00,
    //                   "quarterlyPrice": 999.00,
    //                   "yearlyPrice": 3599.00
    //                 }
    //                 """))
    //         .andExpect(status().isBadRequest())
    //         .andExpect(jsonPath("$.errors.monthlyPrice").exists());
    // }

    /**
     * Verifies subscription creation accepts billing-cycle JSON and returns HTTP 201.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect subscription purchase routing.
     */
    @Test
    void createsSubscriptionThroughRestApi() throws Exception {
        when(subscriptionService.subscribe(any(SubscriptionDtos.CreateRequest.class)))
            .thenReturn(subscriptionResponse());

        mockMvc.perform(post("/api/v1/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "tierId": "%s",
                      "billingCycle": "MONTHLY",
                      "paymentMethodId": "%s"
                    }
                    """.formatted(USER_ID, TIER_ID, PAYMENT_METHOD_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.currentTierCode").value("GOLD"));
    }

    /**
     * Verifies an immediate paid tier upgrade is routed with its payment method.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect paid upgrade routing.
     */
    @Test
    void upgradesPaidTierThroughRestApi() throws Exception {
        when(subscriptionService.upgrade(
            eq(SUBSCRIPTION_ID), any(SubscriptionDtos.UpgradeRequest.class)))
            .thenReturn(subscriptionResponse());

        mockMvc.perform(post(
                "/api/v1/subscriptions/{id}/upgrade", SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "newTierId": "%s",
                      "paymentMethodId": "%s"
                    }
                    """.formatted(TIER_ID, PAYMENT_METHOD_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minTierCode").value("GOLD"))
            .andExpect(jsonPath("$.currentTierCode").value("GOLD"));
    }

    /**
     * Verifies a paid tier downgrade is scheduled through the REST API.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect next-renewal downgrade routing.
     */
    @Test
    void schedulesTierDowngradeThroughRestApi() throws Exception {
        SubscriptionDtos.Response response = new SubscriptionDtos.Response(
            SUBSCRIPTION_ID, USER_ID, "member@example.com",
            BillingCycle.MONTHLY, "Monthly",
            TIER_ID, "GOLD", "Gold",
            TIER_ID, "GOLD", "Gold",
            null, null, TIER_ID, "SILVER",
            SubscriptionStatus.ACTIVE, START, EXPIRY, false, null,
            new BigDecimal("100.00"), "INR", PAYMENT_METHOD_ID, 0);
        when(subscriptionService.downgrade(
            eq(SUBSCRIPTION_ID), any(SubscriptionDtos.DowngradeRequest.class)))
            .thenReturn(response);

        mockMvc.perform(post(
                "/api/v1/subscriptions/{id}/downgrade", SUBSCRIPTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "newTierId": "%s"
                    }
                    """.formatted(TIER_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentTierCode").value("GOLD"))
            .andExpect(jsonPath("$.scheduledMinTierCode").value("SILVER"));
    }

    /**
     * Verifies the selectable membership options expose explicit plans and tier prices.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect plan plus tier selection.
     */
    @Test
    void listsExplicitMembershipOptionsThroughRestApi() throws Exception {
        when(planService.findOptions()).thenReturn(List.of(optionResponse()));

        mockMvc.perform(get("/api/v1/membership-options"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].billingCycle").value("MONTHLY"))
            .andExpect(jsonPath("$[0].tierCode").value("GOLD"))
            .andExpect(jsonPath("$[0].price").value(100.00));
    }

    /**
     * Verifies administrators can configure all duration prices for one tier.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect configurable subscription pricing.
     */
    @Test
    void updatesDurationPricesThroughAdminRestApi() throws Exception {
        when(planService.updateDurationPrices(
            eq(TIER_ID), any(PlanDtos.DurationPricingRequest.class)))
            .thenReturn(List.of(optionResponse()));

        mockMvc.perform(patch(
                "/api/v1/admin/tiers/{tierId}/subscription-prices", TIER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "monthlyPrice": 100.00,
                      "quarterlyPrice": 250.00,
                      "yearlyPrice": 850.00,
                      "currency": "INR"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].price").value(100.00));
    }

    /**
     * Verifies subscription cancellation delegates to the lifecycle service.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect period-end cancellation routing.
     */
    @Test
    void cancelsSubscriptionThroughRestApi() throws Exception {
        SubscriptionDtos.Response cancelled = new SubscriptionDtos.Response(
            SUBSCRIPTION_ID, USER_ID, "member@example.com", TIER_ID, "GOLD", "Gold",
            BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, START, EXPIRY, true, START,
            new BigDecimal("299.00"), "INR", 1);
        when(subscriptionService.cancel(SUBSCRIPTION_ID)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/subscriptions/{id}/cancel", SUBSCRIPTION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));
    }

    /**
     * Verifies administrative perk creation accepts flexible configuration JSON.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect independent perk catalogue creation.
     */
    @Test
    void createsPerkThroughAdminRestApi() throws Exception {
        when(perkService.create(any(PerkDtos.CreateRequest.class))).thenReturn(perkResponse());

        mockMvc.perform(post("/api/v1/admin/perks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "EXTRA_DISCOUNT",
                      "name": "Extra discount",
                      "type": "PERCENTAGE_DISCOUNT",
                      "configuration": {"discountPercent": 10}
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("EXTRA_DISCOUNT"))
            .andExpect(jsonPath("$.configuration.discountPercent").value(10));
    }

    /**
     * Verifies administrators can assign a perk to a tier.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect tier-perk assignment routing.
     */
    @Test
    void assignsPerkToTierThroughAdminRestApi() throws Exception {
        when(perkService.assign(eq(TIER_ID), eq(PERK_ID),
            any(PerkDtos.AssignmentRequest.class))).thenReturn(assignmentResponse());

        mockMvc.perform(put("/api/v1/admin/tiers/{tierId}/perks/{perkId}", TIER_ID, PERK_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effectiveConfiguration.discountPercent").value(10));
    }

    /**
     * Verifies administrators can remove a perk assignment without deleting the perk.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect tier-perk unassignment routing.
     */
    @Test
    void removesPerkFromTierThroughAdminRestApi() throws Exception {
        mockMvc.perform(delete(
                "/api/v1/admin/tiers/{tierId}/perks/{perkId}", TIER_ID, PERK_ID))
            .andExpect(status().isNoContent());

        verify(perkService).unassign(TIER_ID, PERK_ID);
    }

    /**
     * Verifies checkout can evaluate the best current-subscription perk discount.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect user discount evaluation routing.
     */
    @Test
    void evaluatesUserDiscountThroughRestApi() throws Exception {
        DiscountDtos.EvaluationResponse response = new DiscountDtos.EvaluationResponse(
            USER_ID, SUBSCRIPTION_ID, TIER_ID,
            new BigDecimal("1600.00"), new BigDecimal("20.00"),
            new BigDecimal("320.00"), new BigDecimal("1280.00"), true);
        when(discountService.evaluate(eq(USER_ID),
            any(DiscountDtos.EvaluationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users/{userId}/discount/evaluate", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"orderAmount": 1600.00}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discountPercent").value(20.00))
            .andExpect(jsonPath("$.discountAmount").value(320.00))
            .andExpect(jsonPath("$.finalAmount").value(1280.00));
    }

    /**
     * Verifies the user-perks endpoint returns subscription context and active entitlements.
     *
     * @return no return value
     * @throws Exception when MockMvc cannot execute the request
     * @implNote Used by Maven's test lifecycle to protect membership home-screen entitlement responses.
     */
    @Test
    void returnsUserPerksThroughRestApi() throws Exception {
        PerkDtos.UserPerksResponse response = new PerkDtos.UserPerksResponse(
            USER_ID, SUBSCRIPTION_ID, TIER_ID, "GOLD", EXPIRY,
            List.of(assignmentResponse()));
        when(perkService.getUserPerks(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/{userId}/perks", USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tierCode").value("GOLD"))
            .andExpect(jsonPath("$.perks[0].perkCode").value("EXTRA_DISCOUNT"));
    }

    /**
     * Creates the shared user response fixture.
     *
     * @return user response with a stable UUID and timestamp
     * @implNote Used internally by user controller tests.
     */
    private UserDtos.Response userResponse() {
        return new UserDtos.Response(
            USER_ID, "member@example.com", "Member", "User", null, true, START);
    }

    /**
     * Creates the shared tier response fixture.
     *
     * @return active Gold tier response
     * @implNote Used internally by tier controller tests.
     */
    private TierDtos.Response tierResponse() {
        return new TierDtos.Response(
            TIER_ID, "GOLD", "Gold", "Gold tier", 2,
            new BigDecimal("299.00"), new BigDecimal("799.00"),
            new BigDecimal("2999.00"), true);
    }

    /**
     * Creates the shared subscription response fixture.
     *
     * @return active monthly subscription response
     * @implNote Used internally by subscription controller tests.
     */
    private SubscriptionDtos.Response subscriptionResponse() {
        return new SubscriptionDtos.Response(
            SUBSCRIPTION_ID, USER_ID, "member@example.com", TIER_ID, "GOLD", "Gold",
            BillingCycle.MONTHLY, SubscriptionStatus.ACTIVE, START, EXPIRY, false, null,
            new BigDecimal("299.00"), "INR", 0);
    }

    /**
     * Creates a shared explicit plan and tier pricing fixture.
     *
     * @return Monthly Gold option priced at INR 100
     * @implNote Used internally by plan catalogue and administrative pricing tests.
     */
    private PlanDtos.OptionResponse optionResponse() {
        return new PlanDtos.OptionResponse(
            BillingCycle.MONTHLY, 1,
            TIER_ID, "GOLD", "Gold", 2,
            new BigDecimal("100.00"), "INR");
    }

    /**
     * Creates the shared perk response fixture.
     *
     * @return active percentage-discount perk response
     * @implNote Used internally by perk controller tests.
     */
    private PerkDtos.Response perkResponse() {
        return new PerkDtos.Response(
            PERK_ID, "EXTRA_DISCOUNT", "Extra discount", "Discount",
            PerkType.PERCENTAGE_DISCOUNT, Map.of("discountPercent", 10), true, 0);
    }

    /**
     * Creates the shared tier assignment response fixture.
     *
     * @return active assignment using the reusable perk configuration
     * @implNote Used internally by admin assignment and user-perk controller tests.
     */
    private PerkDtos.AssignmentResponse assignmentResponse() {
        return new PerkDtos.AssignmentResponse(
            ASSIGNMENT_ID, TIER_ID, PERK_ID, "EXTRA_DISCOUNT", "Extra discount",
            "Discount", PerkType.PERCENTAGE_DISCOUNT, Map.of("discountPercent", 10),
            Map.of("discountPercent", 10), true);
    }
}
