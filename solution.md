# FirstClub Membership Program - Solution

## 1. Overview

This solution implements the membership program described in the assignment as a modular Java
21 and Spring Boot application backed by PostgreSQL.

The application supports:

- Monthly, Quarterly, and Yearly membership plans.
- Silver, Gold, and Platinum membership tiers.
- Configurable prices for every billing-cycle and tier combination.
- Configurable perks assigned to tiers.
- Subscription purchase, prorated upgrade, scheduled downgrade, cancellation, reactivation,
  automatic renewal, and expiry.
- Automatic behavioral tiers based on monthly order count, monthly order value, and user
  cohort.
- A product catalogue, session cart, checkout calculation, and immutable order receipt.
- Session-based authentication with separate member and administrator experiences.
- REST APIs and server-rendered browser interfaces.

The code is organized by business capability rather than as one large service. Packages such
as `subscription`, `tier`, `perk`, `order`, `payment`, `catalog`, and `auth` each own a
specific part of the domain.

## 2. Technology and runtime

- Java 21
- Spring Boot 4
- Spring MVC and Thymeleaf
- Spring Data JPA and Hibernate
- Spring Security with server-side sessions and BCrypt
- PostgreSQL 17
- Flyway database migrations
- Maven Wrapper
- Docker Compose
- JUnit 5, Mockito, AssertJ, and MockMvc

The complete local environment can be started with:

```bash
./setup-and-run.sh
```

The script checks Java and Docker, starts PostgreSQL, creates local administrator
configuration, applies Flyway migrations, and starts the application on port 8080.

## 3. Main domain model

### User

`User` stores the unique login email, BCrypt password hash, name, optional cohort, enabled
state, and role (`MEMBER` or `ADMIN`).

Email uniqueness is checked in the service and protected by a case-insensitive database
unique index.

### Tier

`Tier` represents Silver, Gold, Platinum, or a future tier. It stores:

- stable code and display name;
- ordering rank;
- description;
- active state;
- legacy convenience prices.

The rank makes tier comparison generic. A larger rank means a higher tier, avoiding
hard-coded comparisons throughout the subscription logic.

### PlanTierPrice

`PlanTierPrice` stores pricing for one `BillingCycle + Tier` combination. The supported
billing cycles are represented by the `BillingCycle` enum:

- `MONTHLY`
- `QUARTERLY`
- `YEARLY`

A unique constraint prevents duplicate prices for the same cycle and tier.

### Subscription

`Subscription` separates four tier concepts:

- `minTier`: the tier explicitly paid for by the member;
- `currentTier`: the effective tier currently controlling benefits;
- `computedBehavioralTier`: the tier earned from behavioral rules;
- `scheduledMinTier`: a lower paid tier waiting for the next renewal.

This separation is important because a member may pay for Silver, temporarily earn Gold from
behavior, and schedule a future downgrade without losing current-period benefits.

The entity also stores billing cycle, start and expiry timestamps, status, cancellation state,
price snapshot, currency, and the payment method used for renewal.

### Perk and TierPerk

`Perk` is a reusable benefit definition. It has a unique code and name, a type, description,
active state, and JSON configuration.

Supported perk types include:

- `FREE_DELIVERY`
- `PERCENTAGE_DISCOUNT`
- `EARLY_ACCESS`
- `PRIORITY_SUPPORT`
- `EXCLUSIVE_COUPON`
- `CUSTOM`

`TierPerk` assigns a perk to a tier. A unique database constraint prevents the same perk from
being assigned to the same tier more than once. Configuration belongs to the reusable perk,
so all assignments use one consistent definition.

### Product, CustomerOrder, and OrderItem

`Product` represents an administrator-managed catalogue item. Inventory is intentionally
outside the current scope.

`CustomerOrder` stores the checkout snapshot:

- original subtotal;
- discount percentage and amount;
- applied discount perk code and name;
- delivery fee;
- final amount;
- free-delivery flag;
- membership tier used at checkout.

`OrderItem` snapshots SKU, name, unit price, quantity, and line total. Historical orders
therefore remain accurate even if the product or perk is edited later.

### Payment entities

`UserPaymentMethod` stores safe display metadata and a mock provider token. It never stores a
real card number.

`PaymentTransaction` records successful and failed mock charges, their purpose, amount,
currency, provider reference, and failure reason.

## 4. Membership plans and pricing

The public plan catalogue is assembled by `MembershipPlanService`. It returns every active
tier and billing-cycle combination together with its configured price.

Members can view these options in the membership UI or through the REST API. An administrator
can update plan prices without changing historical subscriptions. Existing subscriptions keep
their `pricePaid` snapshot until an upgrade or renewal occurs.

Calendar-based expiry is handled by `BillingCycle.expiryFrom`. This adds one, three, or twelve
calendar months in UTC rather than approximating a month as a fixed number of days.

## 5. Subscription lifecycle

### New subscription

The browser checkout creates a tokenized mock payment method and submits the selected plan and
tier to `SubscriptionService.subscribe`.

The service:

1. locks and checks that the user has no active subscription;
2. validates the user and selected active tier;
3. resolves the configured plan price;
4. charges the mock payment method;
5. creates the active subscription only after successful payment.

### Upgrade

An upgrade is allowed only to a tier with a higher rank than the paid minimum tier.

The application charges only the remaining-period portion of the full price difference:

```text
charge = (new full price - current full price)
         x remaining seconds / total period seconds
```

The result is rounded to two decimal places. The tier changes immediately, perks update
immediately, and the existing expiry date is preserved.

The checkout page displays the current full price, new full price, and amount due today before
the upgrade is submitted.

### Downgrade

A downgrade is scheduled rather than applied immediately. The member retains the current paid
tier and benefits until expiry.

At renewal:

1. the scheduled lower tier becomes the new paid minimum tier;
2. the stored payment method is charged using the lower tier's current full price;
3. the next billing period begins;
4. the scheduled tier field is cleared.

The member and admin UIs show: `Subscription auto-renews to [tier] on [date]`.

### Cancellation and reactivation

Cancellation sets `cancelAtPeriodEnd`. It does not remove already-paid benefits and does not
issue a refund.

The UI shows: `Subscription expires on [date]`.

Before expiry, the member can reactivate the subscription. This clears the cancellation state
and restores normal automatic renewal.

### Automatic renewal and expiry

`SubscriptionExpiryJob` runs every 60 seconds by default and delegates to the transactional
subscription service.

When a subscription is due:

- scheduled cancellation results in `CANCELLED`;
- successful payment renews from the previous expiry;
- a scheduled downgrade is applied during that renewal;
- missing or failed payment results in `EXPIRED`.

Without a pending downgrade or cancellation, the UI displays:
`Subscription auto-renews on [date]`.

## 6. Membership benefits and checkout

Administrators create reusable perks and assign them to tiers through the admin console.

### Percentage discounts

`DiscountService` loads every active percentage-discount perk available through the
subscription's effective tier.

Each candidate reads:

- `discountPercent`;
- optional `maximumDiscount`.

Discounts do not stack. The service calculates the monetary saving from every valid candidate
and applies the single perk that reduces the bill the most.

### Free delivery

`OrderBenefitService` checks whether the effective tier contains an active
`FREE_DELIVERY` perk.

- With the perk: delivery fee is zero.
- Without the perk: ₹50 is added.

The final calculation is:

```text
final amount = item subtotal - best discount + delivery fee
```

### Other perk types

Early access, priority support, exclusive coupons, and custom benefits can be created and
displayed as configurable entitlements. They are represented independently of checkout
discount logic, making it possible to add dedicated handlers later.

The current percentage-discount evaluator applies to the order subtotal. Product/category
eligibility is an extension point: products already contain category data and checkout accepts
category context, but per-item/category eligibility rules are not yet enforced.

## 7. Shopping integration

Administrators manage the product catalogue through the admin UI.

Members:

1. browse active products;
2. add quantities to a session-backed cart;
3. update or remove cart items;
4. place an order;
5. receive a confirmation page with the complete calculation.

On successful checkout:

- current catalogue prices are resolved server-side;
- membership benefits are calculated;
- order and item snapshots are persisted;
- the cart is cleared;
- behavioral tier rules are reevaluated;
- the browser redirects to an order receipt.

The receipt lists items, subtotal, winning discount perk, free-delivery perk when used,
delivery charge, and final price.

## 8. Behavioral membership tiers

Behavioral movement is implemented with the Strategy pattern.

`BehavioralTierStrategy` defines one independent signal. Current implementations are:

- `OrderCountTierStrategy`
- `MonthlySpendTierStrategy`
- `CohortTierStrategy`

`TierEvaluationService` runs every strategy and selects the result with the highest tier rank.
`Subscription.recalculateCurrentTier` then guarantees:

```text
current tier = max(paid minimum tier, behavioral tier)
```

Behavioral qualification never charges the user and never lowers the member below the tier
they paid for.

### Configurable rules

`BehavioralTierSettings` stores administrator-controlled values for:

- Gold and Platinum monthly order counts;
- Gold and Platinum monthly original order values;
- Gold and Platinum cohort names.

The settings are stored in PostgreSQL, not only in application configuration, so changes take
effect without restarting.

The admin service validates:

- non-negative counts and spend;
- Platinum thresholds not lower than Gold thresholds;
- required and distinct cohort names.

Saving the rules immediately reevaluates every active subscription. Rules are also evaluated
after every successful order.

Order count and spend use the current UTC calendar month. Spend uses the original item
subtotal before discount and delivery.

## 9. Authentication and authorization

The application uses Spring Security form login and server-side HTTP sessions.

- Passwords are BCrypt hashed.
- Session cookies are HTTP-only and SameSite Lax.
- Browser forms use CSRF protection.
- Session fixation protection is enabled.
- Only one concurrent session is allowed per account.
- Administrators and members have separate roles.

`AccountAuthorization` protects user, subscription, order, payment, perk, and discount
resources by verifying ownership. Administrators may access administrative resources, while a
member cannot access another member's data.

The first admin account can be bootstrapped from environment variables or the local
`.firstclub.env` generated by the setup script.

## 10. Administration UI

The admin console supports:

- product creation and removal;
- perk creation and safe deletion;
- tier-perk assignment and removal;
- manual subscription creation and ending;
- behavioral rule configuration;
- subscription state and expiry review;
- scheduled status display.

The scheduled-status column distinguishes:

- cancellation at expiry;
- renewal into a downgraded tier;
- normal renewal into the current paid tier;
- no scheduled renewal for inactive subscriptions.

Assigned perks cannot be deleted until they are removed from every tier.

## 11. REST API design

Controllers expose JSON APIs for users, tiers, plan options, subscriptions, perks, payments,
discount evaluation, and orders.

The design uses request/response DTO records rather than exposing JPA entities directly.
Bean Validation handles malformed input, and `ApiExceptionHandler` converts failures into
consistent RFC 9457 problem responses:

- 400 for validation errors;
- 404 for missing resources;
- 409 for domain, uniqueness, or concurrent-update conflicts.

The Thymeleaf browser controllers call the same application services as the REST controllers,
so UI and API workflows share the same business rules.

## 12. Persistence and migrations

Flyway migrations create and evolve the PostgreSQL schema. The migrations cover:

- membership core;
- perks and tier assignments;
- orders;
- mock payments;
- plan pricing and paid/effective tier separation;
- renewal payment methods;
- session-authentication fields;
- product catalogue and order items;
- case-insensitive uniqueness;
- delivery fee and simplified perk assignments;
- applied-discount snapshots;
- behavioral tier settings.

Database constraints protect invariants even when requests race:

- one active subscription per user;
- unique tier code, name, and rank;
- unique perk code and name;
- unique product SKU and name;
- one assignment per tier and perk;
- one plan price per billing cycle and tier;
- valid non-negative monetary values.

## 13. Concurrency considerations

Every auditable entity includes a JPA `@Version` field for optimistic locking. A stale update
is returned as HTTP 409 with retry guidance.

Subscription lifecycle mutations use a pessimistic write lock on the user's active
subscription. This serializes competing subscribe, upgrade, downgrade, cancel, and reactivate
operations.

A partial unique index guarantees one active subscription per user at the database level,
including under concurrent requests.

Payment transactions run in a new transaction. This preserves the audit record of a mock
payment attempt independently of the surrounding membership operation.

Case-insensitive database indexes provide a final safeguard against simultaneous duplicate
email, tier, perk, and product writes.

## 14. Extensibility

The solution is designed so new capabilities can be added without rewriting the subscription
core:

- Add a billing duration by extending `BillingCycle` and plan pricing.
- Add a tier as data with a new rank.
- Add a behavioral signal by implementing `BehavioralTierStrategy`.
- Add a perk type to `PerkType` and a dedicated benefit handler.
- Add item/category eligibility by extending discount candidate filtering.
- Replace `MockPaymentProcessor` with a real payment gateway adapter.
- Add inventory as a separate catalogue/fulfilment concern.

Perks and behavioral rules are data-driven, while lifecycle behavior remains in focused domain
services.

## 15. Testing and demonstration

The automated suite contains 103 tests, with the database-dependent Spring context test
disabled by default unless PostgreSQL integration testing is requested.

Coverage includes:

- user and uniqueness rules;
- tier and pricing management;
- subscription purchase and lifecycle transitions;
- prorated upgrades;
- scheduled downgrade renewal;
- cancellation and reactivation;
- payment success and failure;
- perk assignment and deletion safeguards;
- best-discount selection and discount caps;
- free-delivery calculation;
- product and multi-item order creation;
- behavioral strategy evaluation;
- REST routes, ownership, validation, and error responses.

To run the tests:

```bash
./mvnw test
```

To start the demo:

```bash
./setup-and-run.sh
```

Browser documentation is available in `UI_GUIDE.md`, and detailed API examples are available
in `userdoc.md`.

## 16. Requirement traceability

| Assignment requirement | Implementation |
|---|---|
| Monthly, Quarterly, Yearly plans | `BillingCycle`, `PlanTierPrice`, membership option UI/API |
| Plan-specific pricing | Unique cycle-tier price records editable by admin |
| Free delivery | `FREE_DELIVERY` perk removes the ₹50 delivery fee |
| Extra percentage discount | Configurable percentage/cap; best monetary discount wins |
| Exclusive deals/early access | `EARLY_ACCESS` and `EXCLUSIVE_COUPON` perk types |
| Priority support | `PRIORITY_SUPPORT` perk type |
| Configurable tier perks | Independent `Perk` catalogue plus `TierPerk` assignments |
| Select plan and tier | Membership options and checkout UI/API |
| Subscribe | Paid mock checkout creates active subscription |
| Upgrade | Immediate prorated paid-tier upgrade with unchanged expiry |
| Downgrade | Scheduled lower paid tier applied at renewal |
| Cancel | Period-end cancellation with reactivation option |
| Track membership and expiry | Member home, admin console, current/history APIs |
| Order-count tier movement | Configurable current-month order-count strategy |
| Monthly-order-value movement | Configurable current-month spend strategy |
| Cohort-based movement | Configurable cohort strategy |
| Shopping integration | Product catalogue, cart, checkout, receipt, benefit snapshots |
| Functional APIs | REST controllers protected by session authentication and ownership |
| Extensibility and modularity | Capability packages, strategy abstraction, reusable perks |
| Concurrency | Optimistic versions, pessimistic subscription lock, unique indexes |

