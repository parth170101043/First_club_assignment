# Implementation Log

## 2026-06-21 12:02:10 IST

- Created `README.md` with PostgreSQL, Docker, application startup, and REST API instructions.
- Created `compose.yaml` for automatic PostgreSQL database and user provisioning.
- Modified `pom.xml` to use PostgreSQL and the required Spring Boot dependencies.
- Modified `src/main/resources/application.properties` with PostgreSQL, Flyway, JPA, and expiry settings.
- Created `src/main/resources/db/migration/V1__create_membership_core.sql` for users, tiers, subscriptions, indexes, constraints, and seed tiers.
- Modified `src/main/java/com/example/FirstClubApp/FirstClubAppApplication.java` to enable scheduled processing.
- Created `src/main/java/com/example/FirstClubApp/common/AuditableEntity.java`.
- Created `src/main/java/com/example/FirstClubApp/common/ApiExceptionHandler.java`.
- Created `src/main/java/com/example/FirstClubApp/common/ConflictException.java`.
- Created `src/main/java/com/example/FirstClubApp/common/ResourceNotFoundException.java`.
- Created `src/main/java/com/example/FirstClubApp/config/SecurityConfig.java`.
- Created `src/main/java/com/example/FirstClubApp/user/User.java`.
- Created `src/main/java/com/example/FirstClubApp/user/UserDtos.java`.
- Created `src/main/java/com/example/FirstClubApp/user/UserRepository.java`.
- Created `src/main/java/com/example/FirstClubApp/user/UserService.java`.
- Created `src/main/java/com/example/FirstClubApp/user/UserController.java`.
- Created `src/main/java/com/example/FirstClubApp/tier/Tier.java`.
- Created `src/main/java/com/example/FirstClubApp/tier/TierDtos.java`.
- Created `src/main/java/com/example/FirstClubApp/tier/TierRepository.java`.
- Created `src/main/java/com/example/FirstClubApp/tier/TierService.java`.
- Created `src/main/java/com/example/FirstClubApp/tier/TierController.java`.
- Created `src/main/java/com/example/FirstClubApp/subscription/BillingCycle.java`.
- Created `src/main/java/com/example/FirstClubApp/subscription/SubscriptionStatus.java`.
- Created `src/main/java/com/example/FirstClubApp/subscription/Subscription.java`.
- Created `src/main/java/com/example/FirstClubApp/subscription/SubscriptionDtos.java`.
- Created `src/main/java/com/example/FirstClubApp/subscription/SubscriptionRepository.java`.
- Created `src/main/java/com/example/FirstClubApp/subscription/SubscriptionService.java`.
- Created `src/main/java/com/example/FirstClubApp/subscription/SubscriptionController.java`.
- Created `src/main/java/com/example/FirstClubApp/subscription/SubscriptionExpiryJob.java`.
- Modified `src/test/java/com/example/FirstClubApp/FirstClubAppApplicationTests.java`.
- Created `src/test/java/com/example/FirstClubApp/subscription/BillingCycleTest.java`.
- Created `Impl.md` to record every implementation file creation and modification with a timestamp.
- Added disciplined Javadoc to every Java class and explicit method, including objective, parameters, return value, defaults, and caller.
- Added objective comments to the SQL migration, Maven configuration, application properties, and Docker Compose configuration.

## 2026-06-21 12:09:15 IST

- Modified `src/main/java/com/example/FirstClubApp/user/UserController.java` to expose
  `DELETE /api/v1/users/{id}` with HTTP 204 for successful test-user cleanup.
- Modified `src/main/java/com/example/FirstClubApp/user/UserService.java` to safely delete users
  only when no current or historical subscription references them.
- Modified `src/main/java/com/example/FirstClubApp/subscription/SubscriptionRepository.java` to
  provide the documented user subscription-history existence check.
- Modified `README.md` with create, list, retrieve, and delete user API examples.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 14:48:59 IST

- Corrected `userdoc.md` so its order section accurately states that order-count,
  current-month-spend, and cohort strategies are implemented and update
  `computedBehavioralTier` and `currentTier`.
- Modified `README.md` and `userdoc.md` with state-idempotency, concurrency, tier-deactivation,
  and deliberate authentication-scope notes.
- Modified `src/main/java/com/example/FirstClubApp/subscription/Subscription.java` so applying
  an unchanged behavioral tier is a no-op.
- Modified `src/main/java/com/example/FirstClubApp/common/ApiExceptionHandler.java` so optimistic
  locking failures return HTTP `409 Conflict` with refresh-and-retry guidance.
- Modified `src/test/java/com/example/FirstClubApp/subscription/SubscriptionPlanLifecycleTest.java`
  with repeated behavioral reevaluation coverage.
- Modified `src/test/java/com/example/FirstClubApp/common/ApiControllerTest.java` with concurrent
  update conflict-response coverage.
- Verified 103 tests with 0 failures, 0 errors, and 1 optional PostgreSQL context test skipped.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 14:42:53 IST

- Created `src/main/resources/db/migration/V6__connect_subscription_payments.sql` to store the
  renewal payment method on subscriptions.
- Modified `src/main/java/com/example/FirstClubApp/subscription/Subscription.java`,
  `SubscriptionDtos.java`, and `SubscriptionService.java` so initial purchase charges the full
  price, paid upgrades charge the difference and replace the renewal method, and renewals charge
  the stored method before applying a scheduled downgrade.
- Created `src/main/java/com/example/FirstClubApp/tier/BehavioralTierStrategy.java`,
  `OrderCountTierStrategy.java`, `MonthlySpendTierStrategy.java`, and `CohortTierStrategy.java`
  as independent, configurable behavioral signals.
- Modified `src/main/java/com/example/FirstClubApp/tier/TierEvaluationService.java` to select the
  highest strategy result and apply it as `computedBehavioralTier`; the subscription continues
  to calculate `currentTier` as the maximum of paid and behavioral tiers.
- Modified `src/main/java/com/example/FirstClubApp/order/OrderRepository.java` with current-month
  order count and original-spend aggregates.
- Modified `src/main/java/com/example/FirstClubApp/tier/TierRepository.java` and
  `TierService.java` with active tier lookup by stable code.
- Modified `src/main/resources/application.properties` with configurable order-count,
  monthly-spend, and cohort thresholds.
- Created `src/test/java/com/example/FirstClubApp/tier/BehavioralTierStrategyTest.java`.
- Modified `src/test/java/com/example/FirstClubApp/tier/TierEvaluationServiceTest.java`,
  `src/test/java/com/example/FirstClubApp/subscription/SubscriptionPlanLifecycleTest.java`, and
  `src/test/java/com/example/FirstClubApp/common/ApiControllerTest.java` with behavioral,
  purchase-payment, upgrade, downgrade, renewal-success, and renewal-failure coverage.
- Modified `README.md` and `userdoc.md` with payment-backed subscription actions and behavioral
  tier configuration.
- Verified Flyway V6 against PostgreSQL 17.10 and ran 101 tests with 0 failures, 0 errors, and
  0 skipped.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 14:31:03 IST

- Created `src/main/resources/db/migration/V5__add_membership_plans_and_tier_state.sql` with
  configurable `(billing_cycle, tier)` prices and separate paid, effective, behavioral, and
  scheduled tier columns.
- Created `src/main/java/com/example/FirstClubApp/plan/PlanTierPrice.java` and
  `PlanTierPriceRepository.java` for persisted Monthly, Quarterly, and Yearly tier pricing.
- Created `src/main/java/com/example/FirstClubApp/plan/PlanDtos.java`,
  `MembershipPlanService.java`, and `MembershipPlanController.java` for enum-based billing-cycle
  discovery, priced membership options, and administrative pricing.
- Modified `src/main/java/com/example/FirstClubApp/subscription/Subscription.java` with
  `minTier`, `currentTier`, `computedBehavioralTier`, and `scheduledMinTier`, plus immediate
  upgrades, scheduled downgrades, and renewal behavior.
- Modified `src/main/java/com/example/FirstClubApp/subscription/SubscriptionDtos.java`,
  `SubscriptionService.java`, and `SubscriptionController.java` for explicit
  `billingCycle + tier` purchases, paid upgrades, scheduled downgrades, and renewal processing.
- Modified `src/main/java/com/example/FirstClubApp/admin/AdminTierController.java` with
  `PATCH /api/v1/admin/tiers/{tierId}/subscription-prices`.
- Modified `src/main/java/com/example/FirstClubApp/payment/PaymentService.java` so failed mock
  upgrade payments remain independently persisted.
- Modified `src/main/java/com/example/FirstClubApp/home/HomePageView.java` to display the
  effective tier while deciding upgrade availability from the paid minimum tier.
- Created `src/test/java/com/example/FirstClubApp/plan/MembershipPlanServiceTest.java` and
  `src/test/java/com/example/FirstClubApp/subscription/SubscriptionPlanLifecycleTest.java`.
- Modified `src/test/java/com/example/FirstClubApp/common/ApiControllerTest.java` with REST
  coverage for membership options, configurable prices, paid upgrades, and scheduled downgrades.
- Modified `README.md` and `userdoc.md` with the final enum-based plan model, API examples,
  tier-state meanings, payment behavior, and renewal semantics.
- Verified the complete Maven suite: 96 tests, 0 failures, 0 errors, and 1 PostgreSQL integration
  test skipped unless explicitly enabled.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 14:00:35 IST

- Created `src/main/resources/db/migration/V4__create_mock_payments.sql` for tokenized user
  payment methods, one active default method, and immutable payment transaction history.
- Created `src/main/java/com/example/FirstClubApp/payment/PaymentMethodType.java`.
- Created `src/main/java/com/example/FirstClubApp/payment/PaymentStatus.java`.
- Created `src/main/java/com/example/FirstClubApp/payment/UserPaymentMethod.java`.
- Created `src/main/java/com/example/FirstClubApp/payment/PaymentTransaction.java`.
- Created `src/main/java/com/example/FirstClubApp/payment/PaymentMethodRepository.java`.
- Created `src/main/java/com/example/FirstClubApp/payment/PaymentTransactionRepository.java`.
- Created `src/main/java/com/example/FirstClubApp/payment/MockPaymentProcessor.java` with
  deterministic success and `fail_` token decline behavior.
- Created `src/main/java/com/example/FirstClubApp/payment/PaymentDtos.java`.
- Created `src/main/java/com/example/FirstClubApp/payment/PaymentService.java`.
- Created `src/main/java/com/example/FirstClubApp/payment/PaymentController.java` with payment
  method creation, listing, deactivation, mock charging, and transaction history APIs.
- Modified `src/main/java/com/example/FirstClubApp/user/UserService.java` to protect users with
  payment transaction history from hard deletion.
- Created `src/test/java/com/example/FirstClubApp/payment/MockPaymentProcessorTest.java`.
- Created `src/test/java/com/example/FirstClubApp/payment/PaymentServiceTest.java`.
- Created `src/test/java/com/example/FirstClubApp/payment/PaymentControllerTest.java`.
- Modified `src/test/java/com/example/FirstClubApp/user/UserServiceTest.java` with payment-history
  deletion protection coverage.
- Modified `README.md` and `userdoc.md` with safe mock payment workflows and examples.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 13:52:47 IST

- Created `src/main/resources/db/migration/V3__create_orders.sql` for durable order and applied
  membership benefit snapshots.
- Created `src/main/java/com/example/FirstClubApp/order/CustomerOrder.java`.
- Created `src/main/java/com/example/FirstClubApp/order/OrderRepository.java`.
- Created `src/main/java/com/example/FirstClubApp/order/OrderDtos.java`.
- Created `src/main/java/com/example/FirstClubApp/order/OrderBenefitService.java` to apply
  configured discounts and active free-delivery perks during checkout.
- Created `src/main/java/com/example/FirstClubApp/order/OrderService.java` to persist orders and
  directly trigger post-order tier reevaluation.
- Created `src/main/java/com/example/FirstClubApp/order/OrderController.java` with order creation,
  lookup, and user history APIs.
- Created `src/main/java/com/example/FirstClubApp/tier/TierEvaluationService.java` as the
  order-triggered extension point for future behavioral tier strategies.
- Modified `src/main/java/com/example/FirstClubApp/subscription/SubscriptionService.java` with
  optional active-subscription lookup for non-member checkout.
- Modified `src/main/java/com/example/FirstClubApp/discount/DiscountService.java` to evaluate an
  already resolved active subscription.
- Modified `src/main/java/com/example/FirstClubApp/user/UserService.java` to protect users with
  order history from hard deletion.
- Created `src/test/java/com/example/FirstClubApp/order/OrderBenefitServiceTest.java`.
- Created `src/test/java/com/example/FirstClubApp/order/OrderServiceTest.java`.
- Created `src/test/java/com/example/FirstClubApp/order/OrderControllerTest.java`.
- Created `src/test/java/com/example/FirstClubApp/tier/TierEvaluationServiceTest.java`.
- Modified `src/test/java/com/example/FirstClubApp/user/UserServiceTest.java` with order-history
  deletion protection coverage.
- Modified `README.md` and `userdoc.md` with checkout examples and current reevaluation behavior.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 13:45:41 IST

- Created `src/main/java/com/example/FirstClubApp/discount/DiscountDtos.java` with validated
  threshold configuration and order evaluation contracts.
- Created `src/main/java/com/example/FirstClubApp/discount/DiscountService.java` to create or
  replace tier discount rules, enforce unique thresholds, select the highest matching rule,
  and calculate discount and final order amounts.
- Created `src/main/java/com/example/FirstClubApp/admin/AdminDiscountController.java` with
  `PUT /api/v1/admin/tiers/{tierId}/discount-rules`.
- Created `src/main/java/com/example/FirstClubApp/discount/UserDiscountController.java` with
  `POST /api/v1/users/{userId}/discount/evaluate`.
- Modified `src/main/java/com/example/FirstClubApp/perk/PerkRepository.java` with reusable perk
  lookup by code.
- Created `src/test/java/com/example/FirstClubApp/discount/DiscountServiceTest.java` for rule
  sorting, duplicate thresholds, strict boundaries, highest-rule selection, and no-rule cases.
- Modified `src/test/java/com/example/FirstClubApp/common/ApiControllerTest.java` with discount
  administration, evaluation, and percentage validation tests.
- Modified `README.md` and `userdoc.md` with configuration and checkout examples.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 12:50:03 IST

- Created `src/main/java/com/example/FirstClubApp/admin/AdminTierController.java` with
  `PATCH /api/v1/admin/tiers/{tierId}/prices`.
- Modified `src/main/java/com/example/FirstClubApp/tier/TierDtos.java` with a validated
  `PriceUpdateRequest` for Monthly, Quarterly, and Yearly prices.
- Modified `src/main/java/com/example/FirstClubApp/tier/Tier.java` with a focused price update
  method that preserves all non-price tier fields.
- Modified `src/main/java/com/example/FirstClubApp/tier/TierService.java` with transactional
  administrative price update behavior.
- Modified `src/test/java/com/example/FirstClubApp/tier/TierServiceTest.java` with focused price
  update coverage.
- Modified `src/test/java/com/example/FirstClubApp/common/ApiControllerTest.java` with successful
  admin price update and negative-price validation tests.
- Modified `README.md` and `userdoc.md` with the admin pricing endpoint and price snapshot rules.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 12:41:59 IST

- Created `userdoc.md` with a complete user-facing guide for Docker and PostgreSQL startup,
  application execution, users, tiers, subscriptions, cancellation, reactivation, expiry,
  independent perks, tier assignments, user entitlements, the HTML homepage, validation,
  errors, tests, and planned authentication work.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 12:34:44 IST

- Modified `pom.xml` to add Spring Boot Thymeleaf support for server-rendered HTML.
- Created `src/main/java/com/example/FirstClubApp/home/HomePageView.java` for presentation-ready
  user, subscription, upgrade, expiry, and perk information.
- Created `src/main/java/com/example/FirstClubApp/home/HomePageService.java` to aggregate existing
  user, subscription, and user-perk services.
- Created `src/main/java/com/example/FirstClubApp/home/HomePageController.java` with the browser
  route `GET /users/{userId}/home`.
- Created `src/main/resources/templates/user-home.html` with a responsive FirstClub membership
  homepage, subscription card, upgrade behavior, cancellation notice, and perk cards.
- Created `src/test/java/com/example/FirstClubApp/home/HomePageServiceTest.java` for Gold upgrade,
  Platinum highest-tier, expiry formatting, and perk aggregation behavior.
- Created `src/test/java/com/example/FirstClubApp/home/HomePageControllerTest.java` for homepage
  model population and Thymeleaf view selection.
- Modified `README.md` with the user homepage route and displayed behavior.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 12:26:26 IST

- Created `src/test/java/com/example/FirstClubApp/testutil/TestEntityFactory.java` for documented
  initialization of JPA-managed fields in database-free unit tests.
- Created `src/test/java/com/example/FirstClubApp/user/UserServiceTest.java` for user creation,
  normalization, duplicate prevention, listing, lookup, and protected deletion.
- Created `src/test/java/com/example/FirstClubApp/tier/TierServiceTest.java` for tier creation,
  code and rank uniqueness, listing, updates, activation checks, and missing-tier behavior.
- Created `src/test/java/com/example/FirstClubApp/subscription/SubscriptionServiceTest.java` for
  subscription creation, pricing, expiry dates, duplicate prevention, current membership,
  history, cancellation, reactivation, expiry processing, and missing memberships.
- Created `src/test/java/com/example/FirstClubApp/subscription/SubscriptionExpiryJobTest.java`
  for scheduled expiry delegation.
- Created `src/test/java/com/example/FirstClubApp/perk/PerkServiceTest.java` for perk creation,
  duplicate prevention, listing, updates, deactivation, assignment, override updates,
  unassignment, and user entitlement filtering.
- Created `src/test/java/com/example/FirstClubApp/common/ApiControllerTest.java` for REST routes,
  JSON contracts, validation, HTTP status codes, and problem-response translation.
- Modified `src/test/java/com/example/FirstClubApp/FirstClubAppApplicationTests.java` so the
  PostgreSQL context integration test runs when `RUN_POSTGRES_INTEGRATION_TESTS=true`.
- Modified `pom.xml` to load the Mockito Byte Buddy agent at JVM startup for reliable Java 21
  test execution.
- Modified `README.md` with test execution modes and feature coverage.
- Modified `Impl.md` with this timestamped implementation record.

## 2026-06-21 12:20:10 IST

- Created `src/main/resources/db/migration/V2__create_perks.sql` for the independent perk
  catalogue, PostgreSQL JSONB configuration, and unique tier assignments.
- Created `src/main/java/com/example/FirstClubApp/perk/PerkType.java`.
- Created `src/main/java/com/example/FirstClubApp/perk/Perk.java`.
- Created `src/main/java/com/example/FirstClubApp/perk/TierPerk.java`.
- Created `src/main/java/com/example/FirstClubApp/perk/PerkRepository.java`.
- Created `src/main/java/com/example/FirstClubApp/perk/TierPerkRepository.java`.
- Created `src/main/java/com/example/FirstClubApp/perk/PerkDtos.java`.
- Created `src/main/java/com/example/FirstClubApp/perk/PerkService.java`.
- Created `src/main/java/com/example/FirstClubApp/perk/UserPerkController.java`.
- Created `src/main/java/com/example/FirstClubApp/admin/AdminPerkController.java`.
- Created `src/main/java/com/example/FirstClubApp/admin/AdminTierPerkController.java`.
- Modified `src/main/java/com/example/FirstClubApp/tier/TierService.java` to expose documented
  tier lookup for perk assignment validation.
- Modified `src/main/java/com/example/FirstClubApp/subscription/SubscriptionService.java` to
  expose active subscription lookup for user entitlement resolution.
- Created `src/test/java/com/example/FirstClubApp/perk/TierPerkTest.java`.
- Modified `README.md` with perk catalogue, assignment, removal, and user entitlement examples.
- Modified `Impl.md` with this timestamped implementation record.
