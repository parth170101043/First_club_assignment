# FirstClub User Guide

This document describes the features currently implemented in the FirstClub membership
application and provides examples for testing them.

For browser-only member and administrator instructions, see [UI_GUIDE.md](UI_GUIDE.md).

> Authentication uses Spring Security sessions, BCrypt password hashes, CSRF-protected browser
> forms, and MEMBER/ADMIN authorization.
> Admin routes require `ROLE_ADMIN`, while member resources enforce authenticated ownership.

Administrators are redirected to `/admin` after login. The admin console replaces the common
curl workflows for products, perks, tier assignments, subscriptions, and behavioral rules. Perks must first be
removed from every membership tier before they can be deleted.

## 1. Current features

- PostgreSQL database managed through Docker Compose
- Automatic database migrations with Flyway
- User creation, listing, retrieval, and safe deletion
- Silver, Gold, and Platinum membership tiers
- Monthly, Quarterly, and Yearly billing cycles
- Subscription creation, current membership, and history
- Period-end cancellation with no refund
- Cancellation reactivation before expiry
- Automatic subscription expiry processing
- Independent configurable perk catalogue
- Tier perk assignments using the reusable perk configuration
- User perk resolution based on the active subscription tier
- Responsive server-rendered user homepage
- Request validation and consistent API error responses
- Unit and REST contract tests

## 2. Start the application

### Requirements

- Java 21
- Docker Desktop

Docker Desktop must be installed separately. Maven does not need to be installed because the
project contains the Maven Wrapper.

### Start PostgreSQL

Open Docker Desktop and wait for the Docker engine to start. From the project directory, run:

```bash
docker compose up -d
```

Docker automatically creates:

```text
Host: localhost
Port: 5432
Database: firstclub
Username: firstclub
Password: firstclub
```

No manual SQL command is required.

### Start Spring Boot

```bash
./mvnw spring-boot:run
```

The application becomes available at:

```text
http://localhost:8080
```

Flyway automatically creates the tables and inserts the initial Silver, Gold, and Platinum
tiers.

## 3. Suggested testing workflow

Use this order when testing the application:

1. Create a user.
2. List tiers and copy a tier ID.
3. Create one or more perks.
4. Assign perks to the selected tier.
5. Create a subscription for the user.
6. View the user's available perks.
7. Open the user's HTML homepage.
8. Test cancellation and reactivation.

The examples below use placeholders such as `USER_UUID`, `TIER_UUID`, `PERK_UUID`, and
`SUBSCRIPTION_UUID`. Replace them with IDs returned by the APIs.

## 4. User APIs

### Create a user

```http
POST /api/v1/users
```

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alex@example.com",
    "firstName": "Alex",
    "lastName": "Roy",
    "cohort": "EARLY_ADOPTER"
  }'
```

Example response:

```json
{
  "id": "USER_UUID",
  "email": "alex@example.com",
  "firstName": "Alex",
  "lastName": "Roy",
  "cohort": "EARLY_ADOPTER",
  "enabled": true,
  "createdAt": "2026-06-21T07:00:00Z"
}
```

Email addresses are normalized to lowercase. Duplicate email addresses return HTTP
`409 Conflict`.

### List users

```bash
curl http://localhost:8080/api/v1/users
```

### Get one user

```bash
curl http://localhost:8080/api/v1/users/USER_UUID
```

### Delete a test user

```bash
curl -i -X DELETE http://localhost:8080/api/v1/users/USER_UUID
```

A successful deletion returns HTTP `204 No Content`.

Users with current or historical subscriptions cannot be deleted. This protects membership
history and returns HTTP `409 Conflict`.

## 5. Membership tier APIs

Silver, Gold, and Platinum are inserted automatically.

### List active tiers

```bash
curl http://localhost:8080/api/v1/tiers
```

Example tier:

```json
{
  "id": "TIER_UUID",
  "code": "GOLD",
  "name": "Gold",
  "description": "Enhanced membership tier",
  "rank": 2,
  "monthlyPrice": 299.00,
  "quarterlyPrice": 799.00,
  "yearlyPrice": 2999.00,
  "active": true
}
```

### Get one tier

```bash
curl http://localhost:8080/api/v1/tiers/TIER_UUID
```

### Create a tier

This endpoint is restricted to administrators.

```bash
curl -X POST http://localhost:8080/api/v1/tiers \
  -H "Content-Type: application/json" \
  -d '{
    "code": "DIAMOND",
    "name": "Diamond",
    "description": "Premium membership tier",
    "rank": 4,
    "monthlyPrice": 699.00,
    "quarterlyPrice": 1899.00,
    "yearlyPrice": 6999.00
  }'
```

Tier codes and ranks must be unique.

### Update a tier

```bash
curl -X PUT http://localhost:8080/api/v1/tiers/TIER_UUID \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Gold Plus",
    "description": "Updated Gold membership",
    "monthlyPrice": 349.00,
    "quarterlyPrice": 899.00,
    "yearlyPrice": 3299.00,
    "active": true
  }'
```

An inactive tier remains in the database and cannot be selected for a new subscription or paid
tier change. Existing active subscriptions keep their paid/effective tier and assigned perks
for the current period; deactivation is a catalogue restriction, not a retroactive removal.

### Change subscription prices as an administrator

Use the focused admin endpoint when only the Monthly, Quarterly, and Yearly prices need to
change:

```bash
curl -X PATCH http://localhost:8080/api/v1/admin/tiers/TIER_UUID/subscription-prices \
  -H "Content-Type: application/json" \
  -d '{
    "monthlyPrice": 100.00,
    "quarterlyPrice": 250.00,
    "yearlyPrice": 850.00,
    "currency": "INR"
  }'
```

All three prices and the three-letter currency are required. Monthly, Quarterly, and Yearly are
billing-cycle enum values; they are not separate database entities.

Pricing behavior:

- New subscriptions use the configured cycle-tier price.
- Existing subscriptions keep `pricePaid` until an upgrade or renewal.
- Tier name, description, code, rank, and activation state are unchanged.
- This endpoint requires `ROLE_ADMIN`.

## 6. Subscription APIs

### Get billing cycles and priced membership options

```bash
curl http://localhost:8080/api/v1/plans
curl http://localhost:8080/api/v1/membership-options
```

The membership options endpoint returns every active `billingCycle + tier` combination with
its configured price.

### Create a subscription

```bash
curl -X POST http://localhost:8080/api/v1/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "USER_UUID",
    "tierId": "TIER_UUID",
    "billingCycle": "MONTHLY",
    "paymentMethodId": "PAYMENT_METHOD_UUID"
  }'
```

Supported billing cycles:

- `MONTHLY`: one calendar month
- `QUARTERLY`: three calendar months
- `YEARLY`: twelve calendar months

Example response:

```json
{
  "id": "SUBSCRIPTION_UUID",
  "userId": "USER_UUID",
  "userEmail": "alex@example.com",
  "billingCycle": "MONTHLY",
  "planName": "Monthly",
  "minTierId": "TIER_UUID",
  "minTierCode": "GOLD",
  "minTierName": "Gold",
  "currentTierId": "TIER_UUID",
  "currentTierCode": "GOLD",
  "currentTierName": "Gold",
  "computedBehavioralTierId": null,
  "computedBehavioralTierCode": null,
  "scheduledMinTierId": null,
  "scheduledMinTierCode": null,
  "status": "ACTIVE",
  "startsAt": "2026-06-21T07:00:00Z",
  "expiresAt": "2026-07-21T07:00:00Z",
  "cancelAtPeriodEnd": false,
  "cancelledAt": null,
  "pricePaid": 299.00,
  "currency": "INR",
  "version": 0
}
```

Important behavior:

- A user can have only one active subscription.
- Price and expiry are calculated by the server.
- The full initial price is charged before activation.
- The selected payment method is stored for automatic renewal.
- The selected price is stored as a subscription snapshot.
- Concurrent subscription changes are protected by database and locking rules.

Tier meaning:

- `minTier` is explicitly selected and paid for.
- `computedBehavioralTier` is earned from behavioral rules.
- `currentTier` controls benefits and is always the higher of the two.
- `scheduledMinTier` is a downgrade that will apply at renewal.

### Upgrade the paid tier immediately

```bash
curl -X POST \
  http://localhost:8080/api/v1/subscriptions/SUBSCRIPTION_UUID/upgrade \
  -H "Content-Type: application/json" \
  -d '{
    "newTierId": "HIGHER_TIER_UUID",
    "paymentMethodId": "PAYMENT_METHOD_UUID"
  }'
```

The mock payment service charges the difference between the current price snapshot and the
higher tier's configured price. A successful payment updates `minTier` immediately and stores
that payment method for future renewals.

### Schedule a paid-tier downgrade

```bash
curl -X POST \
  http://localhost:8080/api/v1/subscriptions/SUBSCRIPTION_UUID/downgrade \
  -H "Content-Type: application/json" \
  -d '{
    "newTierId": "LOWER_TIER_UUID"
  }'
```

The downgrade is stored in `scheduledMinTier`. It does not remove benefits during the period
already paid for.

### Get the current subscription

```bash
curl http://localhost:8080/api/v1/subscriptions/users/USER_UUID/current
```

A user without an active subscription receives HTTP `404 Not Found`.

### Get subscription history

```bash
curl http://localhost:8080/api/v1/subscriptions/users/USER_UUID/history
```

### Cancel at period end

```bash
curl -X POST \
  http://localhost:8080/api/v1/subscriptions/SUBSCRIPTION_UUID/cancel
```

Cancellation policy:

- No refund is issued.
- The subscription remains `ACTIVE` until its expiry.
- Perks remain available until expiry.
- `cancelAtPeriodEnd` becomes `true`.

### Reactivate a scheduled cancellation

```bash
curl -X POST \
  http://localhost:8080/api/v1/subscriptions/SUBSCRIPTION_UUID/reactivate
```

Reactivation is allowed only while the subscription is active and cancellation is scheduled.

### Subscription expiry

The application checks for expired subscriptions automatically every 60 seconds.

For development testing, expiry can also be triggered manually:

```bash
curl -X POST http://localhost:8080/api/v1/subscriptions/expire-due
```

At period end:

- a scheduled cancellation becomes `CANCELLED` and the user has no active tier
- otherwise the stored payment method is charged
- successful payment renews the subscription, applies `scheduledMinTier`, refreshes
`pricePaid`, and recalculates `currentTier`
- failed payment expires the subscription, leaving the user without an active tier

### Behavioral tier strategies

Order creation reevaluates three independent signals:

1. Orders placed in the current UTC calendar month.
2. Original order spend in the current UTC calendar month.
3. The cohort stored on the user profile.

Each strategy may return a tier. The highest result is saved as `computedBehavioralTier`, and:

```text
currentTier = max(minTier, computedBehavioralTier)
```

Defaults can be changed in `application.properties`:

```properties
membership.behavior.order-count.gold=5
membership.behavior.order-count.platinum=10
membership.behavior.monthly-spend.gold=5000
membership.behavior.monthly-spend.platinum=15000
membership.behavior.cohort.gold=EARLY_ADOPTER
membership.behavior.cohort.platinum=VIP
```

## 7. Perk catalogue APIs

A perk is independent of a tier. This means an administrator can create perks that are not
available with any subscription.

Supported perk types:

- `FREE_DELIVERY`
- `PERCENTAGE_DISCOUNT`
- `EARLY_ACCESS`
- `PRIORITY_SUPPORT`
- `EXCLUSIVE_COUPON`
- `CUSTOM`

### Create an unassigned perk

```bash
curl -X POST http://localhost:8080/api/v1/admin/perks \
  -H "Content-Type: application/json" \
  -d '{
    "code": "EXTRA_DISCOUNT",
    "name": "Extra discount",
    "description": "Additional discount on eligible orders",
    "type": "PERCENTAGE_DISCOUNT",
    "configuration": {
      "discountPercent": 10,
      "maximumDiscount": 500
    }
  }'
```

Creating the perk does not automatically assign it to Silver, Gold, or Platinum.

### List all perks

```bash
curl http://localhost:8080/api/v1/admin/perks
```

The response includes:

- assigned perks
- unassigned perks
- active perks
- inactive perks

### Get one perk

```bash
curl http://localhost:8080/api/v1/admin/perks/PERK_UUID
```

### Update or reactivate a perk

```bash
curl -X PUT http://localhost:8080/api/v1/admin/perks/PERK_UUID \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Extra discount",
    "description": "Updated discount rules",
    "type": "PERCENTAGE_DISCOUNT",
    "configuration": {
      "discountPercent": 12,
      "maximumDiscount": 750
    },
    "active": true
  }'
```

### Deactivate a perk

```bash
curl -i -X DELETE http://localhost:8080/api/v1/admin/perks/PERK_UUID
```

This operation does not physically delete the perk. It deactivates the perk so users no longer
receive it while preserving its configuration and tier assignments.

## 8. Assign perks to subscription tiers

### Assign a perk using its base configuration

```bash
curl -X PUT \
  http://localhost:8080/api/v1/admin/tiers/TIER_UUID/perks/PERK_UUID \
  -H "Content-Type: application/json" \
  -d '{}'
```

### Assign a perk with a tier-specific override

```bash
curl -X PUT \
  http://localhost:8080/api/v1/admin/tiers/TIER_UUID/perks/PERK_UUID \
  -H "Content-Type: application/json" \
  -d '{
    "configurationOverride": {
      "discountPercent": 15,
      "maximumDiscount": 1000
    }
  }'
```

For example:

```text
Base perk: 10% discount
Silver override: 5% discount
Gold override: 10% discount
Platinum override: 15% discount
```

Submitting the assignment again updates the existing override instead of creating a duplicate.

### List perks assigned to a tier

```bash
curl http://localhost:8080/api/v1/admin/tiers/TIER_UUID/perks
```

### Remove a perk from a tier

```bash
curl -i -X DELETE \
  http://localhost:8080/api/v1/admin/tiers/TIER_UUID/perks/PERK_UUID
```

This returns HTTP `204 No Content` and removes only the tier assignment. The perk remains in
the independent perk catalogue.

## 9. User perk API

### Get perks available to a user

```bash
curl http://localhost:8080/api/v1/users/USER_UUID/perks
```

The application:

1. Finds the user's active subscription.
2. Reads the subscription tier.
3. Loads perks assigned to that tier.
4. Excludes inactive and unassigned perks.
5. Uses the reusable configuration stored on each perk.

Example response:

```json
{
  "userId": "USER_UUID",
  "subscriptionId": "SUBSCRIPTION_UUID",
  "tierId": "TIER_UUID",
  "tierCode": "GOLD",
  "subscriptionExpiresAt": "2026-07-21T07:00:00Z",
  "perks": [
    {
      "assignmentId": "ASSIGNMENT_UUID",
      "tierId": "TIER_UUID",
      "perkId": "PERK_UUID",
      "perkCode": "EXTRA_DISCOUNT",
      "perkName": "Extra discount",
      "description": "Additional discount on eligible orders",
      "type": "PERCENTAGE_DISCOUNT",
      "baseConfiguration": {
        "discountPercent": 10,
        "maximumDiscount": 500
      },
      "configurationOverride": {
        "discountPercent": 15,
        "maximumDiscount": 1000
      },
      "effectiveConfiguration": {
        "discountPercent": 15,
        "maximumDiscount": 1000
      },
      "active": true
    }
  ]
}
```

## 10. Configurable order discounts

Discounts are provided only through active `PERCENTAGE_DISCOUNT` perks available from the user's
current subscription tier. There is no separate tier discount-rule configuration endpoint.

### Create and assign a discount perk

```bash
curl -X POST http://localhost:8080/api/v1/admin/perks \
  -H "Content-Type: application/json" \
  -d '{
    "code": "MEMBER_15_PERCENT",
    "name": "15% member discount",
    "description": "Reduces eligible order bills",
    "type": "PERCENTAGE_DISCOUNT",
    "configuration": {
      "discountPercent": 15.00,
      "maximumDiscount": 500.00
    }
  }'
```

Assign the returned perk through
`PUT /api/v1/admin/tiers/TIER_UUID/perks/PERK_UUID`. The assignment may override
`discountPercent` and `maximumDiscount`.

`maximumDiscount` is optional. At checkout, every active percentage-discount perk from the
current subscription tier is calculated independently. Only the perk producing the largest
monetary reduction is applied; discounts never stack.

### Evaluate a user's order

```bash
curl -X POST \
  http://localhost:8080/api/v1/users/USER_UUID/discount/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "orderAmount": 1600.00
  }'
```

Example response:

```json
{
  "userId": "USER_UUID",
  "subscriptionId": "SUBSCRIPTION_UUID",
  "tierId": "TIER_UUID",
  "orderAmount": 1600.00,
  "discountPercent": 20.00,
  "discountAmount": 320.00,
  "finalAmount": 1280.00,
  "applied": true
}
```

Evaluation behavior:

1. The user must have an active subscription.
2. The active subscription determines the membership tier.
3. The application loads active percentage-discount perks assigned to that tier.
4. Every valid candidate is calculated and the largest monetary discount is selected.
5. The discount amount and final amount are rounded to two decimal places.

A user without an active subscription has no tier and receives HTTP `404 Not Found`. Malformed,
inactive, non-positive, and non-percentage perks are ignored.

## 11. Orders and checkout integration

The minimal order service closes the membership and shopping loop. It accepts a user, order
amount, and optional category, then applies the user's active membership benefits.

### Create an order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "USER_UUID",
    "totalAmount": 1600.00,
    "category": "GROCERY"
  }'
```

Order creation performs these operations:

1. Validates that the user exists.
2. Checks whether the user has an active subscription.
3. Applies the single active discount perk that reduces the bill the most.
4. Checks whether the active tier has an active `FREE_DELIVERY` perk.
5. Persists the original amount and applied benefit snapshot.
6. Calls `TierEvaluationService.reevaluate(userId)`.

Example response:

```json
{
  "id": "ORDER_UUID",
  "userId": "USER_UUID",
  "totalAmount": 1600.00,
  "category": "GROCERY",
  "discountPercent": 20.00,
  "discountAmount": 320.00,
  "finalAmount": 1280.00,
  "freeDelivery": true,
  "membershipTierCode": "GOLD",
  "createdAt": "2026-06-21T08:30:00Z"
}
```

The category is optional and currently stored for future category-specific discount eligibility.

When the user has no active subscription:

- the order is still created
- `discountPercent` and `discountAmount` are zero
- `finalAmount` equals `totalAmount`
- `freeDelivery` is false
- `membershipTierCode` is null
- no membership tier is granted by the order alone

### Get one order

```bash
curl http://localhost:8080/api/v1/orders/ORDER_UUID
```

### Get user order history

```bash
curl http://localhost:8080/api/v1/orders/users/USER_UUID
```

Order benefit values are historical snapshots. Later changes to discount rules, perks, or tier
prices do not rewrite previous orders.

### Tier reevaluation status

Every created order directly calls `TierEvaluationService.reevaluate(userId)`. The service
runs the order-count, current-month spend, and cohort strategies independently. It stores the
highest result as `computedBehavioralTier`, then recalculates:

```text
currentTier = max(minTier, computedBehavioralTier)
```

Reevaluation is state-idempotent: running it again with unchanged inputs produces the same tier
state. The subscription skips assigning an unchanged behavioral tier, so JPA does not create an
unnecessary entity update. There is currently no separate tier-change audit-log table, so no
duplicate audit entry can be created.

### Concurrent membership updates

Paid subscription lifecycle changes lock the user's active subscription before changing it, so
two upgrade/downgrade/cancel operations are serialized. All persistent entities also use a JPA
`@Version` field. If concurrent order evaluation or another versioned write uses stale state,
the API returns HTTP `409 Conflict` with instructions to refresh and retry.

## 12. Mock payments

The payment module is intended only for demonstrating payment method ownership and transaction
flows. It does not move real money.

Security rules:

- Do not send or store real card numbers.
- Do not send or store CVVs.
- Do not send or store UPI PINs.
- Use mock provider tokens such as `tok_success_card`.
- API responses never expose the provider token.

Supported payment method types:

- `CARD`
- `UPI`

### Add a tokenized payment method

```bash
curl -X POST \
  http://localhost:8080/api/v1/users/USER_UUID/payment-methods \
  -H "Content-Type: application/json" \
  -d '{
    "type": "CARD",
    "providerToken": "tok_success_card",
    "displayName": "Personal Visa",
    "brand": "VISA",
    "lastFour": "4242",
    "defaultMethod": true
  }'
```

Example response:

```json
{
  "id": "METHOD_UUID",
  "userId": "USER_UUID",
  "type": "CARD",
  "displayName": "Personal Visa",
  "brand": "VISA",
  "lastFour": "4242",
  "defaultMethod": true,
  "active": true,
  "createdAt": "2026-06-21T08:30:00Z"
}
```

Payment method behavior:

- Provider tokens must be unique.
- The first active method automatically becomes the default.
- Selecting a new default removes default status from the previous method.
- Only one active default method is allowed per user.
- `defaultMethod` may be omitted and defaults to `false`, except for the first method.

### Add a mock UPI method

```bash
curl -X POST \
  http://localhost:8080/api/v1/users/USER_UUID/payment-methods \
  -H "Content-Type: application/json" \
  -d '{
    "type": "UPI",
    "providerToken": "tok_success_upi",
    "displayName": "Personal UPI"
  }'
```

### List active methods

```bash
curl http://localhost:8080/api/v1/users/USER_UUID/payment-methods
```

### Remove a method

```bash
curl -i -X DELETE \
  http://localhost:8080/api/v1/users/USER_UUID/payment-methods/METHOD_UUID
```

The operation returns HTTP `204 No Content`. It deactivates the method instead of deleting it,
so existing payment transactions remain valid. If the removed method was the default, another
active method becomes the default when available.

### Create a successful mock charge

```bash
curl -X POST http://localhost:8080/api/v1/payments/charge \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "USER_UUID",
    "paymentMethodId": "METHOD_UUID",
    "amount": 299.00,
    "currency": "INR",
    "purpose": "Gold monthly subscription"
  }'
```

Example response:

```json
{
  "id": "PAYMENT_UUID",
  "userId": "USER_UUID",
  "paymentMethodId": "METHOD_UUID",
  "amount": 299.00,
  "currency": "INR",
  "purpose": "Gold monthly subscription",
  "status": "SUCCEEDED",
  "providerReference": "mock_pay_REFERENCE",
  "failureReason": null,
  "createdAt": "2026-06-21T08:35:00Z"
}
```

### Simulate a failed charge

Register a payment method whose token begins with `fail_`:

```json
{
  "type": "CARD",
  "providerToken": "fail_declined_card",
  "displayName": "Declined test card",
  "brand": "MOCK",
  "lastFour": "0000"
}
```

Charging that method returns a persisted transaction with:

```json
{
  "status": "FAILED",
  "failureReason": "Mock processor declined the payment method."
}
```

Both successful and failed attempts are retained for audit and demonstration.

### Get payment history

```bash
curl http://localhost:8080/api/v1/users/USER_UUID/payments
```

Users with payment transaction history cannot be hard-deleted.

## 13. User homepage

Open the following URL in a browser:

```text
http://localhost:8080/home
```

The homepage displays:

- “Welcome to FirstClub” with the user's first name
- subscription tier
- billing cycle
- expiry date
- scheduled cancellation notice
- active perks available through the subscription
- upgrade option for Silver and Gold users
- highest-tier message for Platinum users

The current upgrade button opens the tier catalogue. The paid upgrade API is available for a
future dedicated upgrade screen.

## 14. Validation and errors

The APIs return `application/problem+json` errors.

Common status codes:


| Status            | Meaning                                                            |
| ----------------- | ------------------------------------------------------------------ |
| `201 Created`     | User, tier, subscription, or perk was created                      |
| `204 No Content`  | User or tier-perk assignment was removed                           |
| `400 Bad Request` | Request validation failed                                          |
| `404 Not Found`   | User, tier, perk, assignment, or active subscription was not found |
| `409 Conflict`    | Duplicate data or an invalid lifecycle operation                   |


Example validation response:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "errors": {
    "email": "must be a well-formed email address",
    "firstName": "must not be blank"
  }
}
```

## 15. Run tests

Run unit and REST contract tests:

```bash
./mvnw test
```

Run the PostgreSQL integration test as well:

```bash
docker compose up -d
RUN_POSTGRES_INTEGRATION_TESTS=true ./mvnw test
```

The current suite covers users, tiers, subscriptions, expiry, perks, assignments, user
entitlements, homepage behavior, validation, REST routes, and error responses.

## 16. Features planned next

Authentication is implemented with server-side sessions so the membership,
pricing, payment, order-benefit, and tier-evaluation domain could be demonstrated end to end.
The current public endpoints are intentional demo scope, not an assumption that production
admin APIs should remain unauthenticated.

- User signup at `/signup`
- User login at `/login`
- Session logout through `POST /logout`
- Password hashing
- `USER` and `ADMIN` roles
- Protection of `/api/v1/admin/**`
- Logged-in user context instead of passing `USER_UUID` in URLs
- Basic admin HTML screens

