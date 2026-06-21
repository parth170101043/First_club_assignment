# FirstClub Membership Application

Backend application for the FirstClub membership program, built with Java 21, Spring Boot,
Maven, and PostgreSQL.

Authentication uses Spring Security server-side sessions with BCrypt passwords. The current build focuses on
the complete membership, payment, order-benefit, and behavioral-tier workflow; endpoints,
including admin routes, are protected by session authentication and role checks.
## One-command setup and run

On macOS or Linux, run:

```bash
./setup-and-run.sh
```

The script installs missing Docker/Java dependencies where supported, starts PostgreSQL,
creates local admin configuration, applies Flyway migrations, and starts the application.
See [UI_GUIDE.md](UI_GUIDE.md) for member and administrator browser instructions.

See [userdoc.md](userdoc.md) for detailed API guidance and usage.

See [API_DESIGN](API_DESIGN.md) for API Usages only.

## Prerequisites

- Java 21
- Docker Desktop, or a separately installed PostgreSQL server

Maven does not need to be installed separately because the project includes Maven Wrapper
(`mvnw`).

## Option 1: Run PostgreSQL with Docker (recommended)

Yes, Docker must be installed separately. On macOS, install and start
[Docker Desktop](https://www.docker.com/products/docker-desktop/).

Verify that Docker is running:

```bash
docker --version
docker info
```

From the project directory, start PostgreSQL:

```bash
docker compose up -d
```

The included `compose.yaml` starts PostgreSQL with:

```text
Host: localhost
Port: 5432
Database: firstclub
Username: firstclub
Password: firstclub
```

Check the container:

```bash
docker compose ps
```

Stop PostgreSQL:

```bash
docker compose down
```

Stop PostgreSQL and delete its stored data:

```bash
docker compose down -v
```

Use the last command carefully because it permanently deletes the local database volume.

## Option 2: Use a local PostgreSQL installation

Docker is not required if PostgreSQL is already installed and running locally.

Create the user and database:

```sql
CREATE USER firstclub WITH PASSWORD 'firstclub';
CREATE DATABASE firstclub OWNER firstclub;
```

The application expects PostgreSQL at:

```text
jdbc:postgresql://localhost:5432/firstclub
```

## Run the application

First, ensure PostgreSQL is running. Then execute:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
mvnw.cmd spring-boot:run
```

The application runs at:

```text
http://localhost:8080
```

## Run tests

```bash
./mvnw test
```

The default suite contains unit and REST contract tests and does not require PostgreSQL. To also
run the full Spring context integration test, start PostgreSQL and enable it explicitly:

```bash
docker compose up -d
RUN_POSTGRES_INTEGRATION_TESTS=true ./mvnw test
```

The suite covers:

- user creation, normalization, lookup, listing, validation, and protected deletion
- tier creation, uniqueness, listing, updates, pricing, and activation checks
- subscription creation, duplicate prevention, current membership, history, cancellation,
  reactivation, expiry, and billing-cycle dates
- perk creation, listing, updates, deactivation, tier assignment, unassignment, and
  user entitlement filtering
- REST routes, JSON responses, HTTP statuses, validation errors, and domain error translation

## Custom database configuration

The default values are defined in `src/main/resources/application.properties`. Override them
with environment variables when necessary:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/firstclub
export DB_USERNAME=firstclub
export DB_PASSWORD=firstclub
./mvnw spring-boot:run
```

## Troubleshooting

### Cannot connect to the Docker daemon

Start Docker Desktop and wait until it reports that the Docker engine is running.

### Port 5432 is already in use

A local PostgreSQL server or another container may already be using the port. Either stop that
server and use Docker, or skip Docker and configure the existing PostgreSQL instance.

### Failed to configure a DataSource

Confirm that PostgreSQL is running and that the URL, database name, username, and password
match the application configuration.

### Useful commands

```bash
docker compose logs postgres
docker compose restart postgres
./mvnw clean test
```

## Current REST APIs

Open `/signup` to create a member account and `/login` to sign in. Successful login creates an
HTTP-only `JSESSIONID` cookie and redirects to `/home`. Browser forms use CSRF protection.

To bootstrap the first administrator, set:

```bash
export FIRSTCLUB_ADMIN_EMAIL=admin@example.com
export FIRSTCLUB_ADMIN_PASSWORD='replace-with-a-strong-password'
```

Admin APIs require `ROLE_ADMIN`; member APIs require authentication and enforce account
ownership for user, subscription, payment, discount, perk, and order resources.

After an administrator signs in, the browser redirects to:

```text
http://localhost:8080/admin
```

The admin console supports product catalogue management, perk creation/deletion,
tier-perk assignment and removal, and manual subscription creation and immediate ending.
A perk cannot be
deleted while it remains assigned to any membership tier.

Members browse active products at `/shop`, keep quantities in a session cart, and place one
multi-item order. Product names and SKUs are unique. Removing a product hides it from future
carts while historical order items retain product name, SKU, price, quantity, and line total
snapshots. Inventory and stock availability are intentionally not tracked.

### Create a user

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

### List users

```bash
curl http://localhost:8080/api/v1/users
```

### Get a user

```bash
curl http://localhost:8080/api/v1/users/USER_UUID
```

### Delete a test user

```bash
curl -i -X DELETE http://localhost:8080/api/v1/users/USER_UUID
```

Successful deletion returns HTTP `204 No Content`. Users with current or historical
subscriptions cannot be deleted because their membership history must remain valid; the API
returns HTTP `409 Conflict` in that case.

### List tiers

Silver, Gold, and Platinum are inserted by the first Flyway migration.

```bash
curl http://localhost:8080/api/v1/tiers
```

### Change tier subscription prices

This endpoint requires an authenticated account with `ROLE_ADMIN`.
is added:

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

Prices are stored by billing cycle plus tier. Monthly, Quarterly, and Yearly remain enum values,
not separate database plan entities.

### List billing cycles and purchasable tier combinations

```bash
curl http://localhost:8080/api/v1/plans
curl http://localhost:8080/api/v1/membership-options
```

### Create a subscription

Copy the user ID and tier ID from the previous responses:

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

Supported billing cycles are `MONTHLY`, `QUARTERLY`, and `YEARLY`.
The configured full subscription price is charged before activation. A failed payment does not
create an active subscription.

The response separates tier responsibilities:

- `minTier`: the tier explicitly purchased by the user
- `computedBehavioralTier`: the highest tier earned from behavioral evaluation
- `currentTier`: the higher of `minTier` and `computedBehavioralTier`
- `scheduledMinTier`: a paid-tier downgrade waiting for the next renewal

### Upgrade the paid tier

The configured price difference is charged through the mock payment service and the upgrade
takes effect immediately. The supplied method becomes the stored renewal payment method:

```bash
curl -X POST \
  http://localhost:8080/api/v1/subscriptions/SUBSCRIPTION_UUID/upgrade \
  -H "Content-Type: application/json" \
  -d '{
    "newTierId": "HIGHER_TIER_UUID",
    "paymentMethodId": "PAYMENT_METHOD_UUID"
  }'
```

### Schedule a paid-tier downgrade

The current paid tier and benefits remain until renewal:

```bash
curl -X POST \
  http://localhost:8080/api/v1/subscriptions/SUBSCRIPTION_UUID/downgrade \
  -H "Content-Type: application/json" \
  -d '{
    "newTierId": "LOWER_TIER_UUID"
  }'
```

At renewal, the scheduled tier becomes `minTier`, its configured cycle price is stored as the
new `pricePaid`, and `currentTier` is recalculated without dropping below the new paid minimum.
Renewal charges the stored payment method first; a failed renewal expires the subscription so
the user has no active tier.

### Behavioral tier evaluation

Every order triggers three independent strategies:

- current-month order count
- current-month original order spend
- user cohort

The highest strategy result becomes `computedBehavioralTier`. Benefits use
`currentTier = max(minTier, computedBehavioralTier)`.

Thresholds are configurable in `application.properties`:

```properties
membership.behavior.order-count.gold=5
membership.behavior.order-count.platinum=10
membership.behavior.monthly-spend.gold=5000
membership.behavior.monthly-spend.platinum=15000
membership.behavior.cohort.gold=EARLY_ADOPTER
membership.behavior.cohort.platinum=VIP
```

Reevaluation is state-idempotent: unchanged order/spend/cohort inputs preserve the same
`computedBehavioralTier` and `currentTier`, and an unchanged behavioral result is not assigned
again. No separate tier-change audit log exists, so reevaluation cannot create duplicate audit
entries.

### Concurrency behavior

Subscription lifecycle operations acquire a pessimistic write lock on the user's active
subscription. Entities also use JPA `@Version` optimistic locking. Concurrent stale writes are
returned as HTTP `409 Conflict`; clients should fetch the latest subscription and retry.

Deactivating a tier prevents new purchases and paid tier changes. Existing active subscriptions
continue using that tier and its assigned perks for their current period.

### Get current subscription

```bash
curl http://localhost:8080/api/v1/subscriptions/users/USER_UUID/current
```

### Get subscription history

```bash
curl http://localhost:8080/api/v1/subscriptions/users/USER_UUID/history
```

### Cancel at period end

Cancellation does not issue a refund. Membership remains active until its expiry date.

```bash
curl -X POST \
  http://localhost:8080/api/v1/subscriptions/SUBSCRIPTION_UUID/cancel
```

### Undo scheduled cancellation

```bash
curl -X POST \
  http://localhost:8080/api/v1/subscriptions/SUBSCRIPTION_UUID/reactivate
```

Expired subscriptions are processed automatically every 60 seconds. They can also be processed
manually during development:

```bash
curl -X POST http://localhost:8080/api/v1/subscriptions/expire-due
```

## Perk APIs

The perk catalogue and tier assignments are separate. Therefore, an administrator can create a
perk without making it available through any subscription tier.

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

### List the complete perk catalogue

This includes assigned, unassigned, active, and inactive perks:

```bash
curl http://localhost:8080/api/v1/admin/perks
```

### Update or reactivate a perk

```bash
curl -X PUT http://localhost:8080/api/v1/admin/perks/PERK_UUID \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Extra discount",
    "description": "Additional discount on eligible orders",
    "type": "PERCENTAGE_DISCOUNT",
    "configuration": {
      "discountPercent": 10,
      "maximumDiscount": 500
    },
    "active": true
  }'
```

### Deactivate a perk

```bash
curl -i -X DELETE http://localhost:8080/api/v1/admin/perks/PERK_UUID
```

The perk is retained in the catalogue and its assignments remain stored, but users no longer
receive it.

### Assign a perk to a subscription tier

The assignment uses the reusable perk's configuration:

```bash
curl -X PUT \
  http://localhost:8080/api/v1/admin/tiers/TIER_UUID/perks/PERK_UUID \
  -H "Content-Type: application/json" \
  -d '{}'
```

### List perks assigned to a tier

```bash
curl http://localhost:8080/api/v1/admin/tiers/TIER_UUID/perks
```

### Remove a perk from a subscription tier

```bash
curl -i -X DELETE \
  http://localhost:8080/api/v1/admin/tiers/TIER_UUID/perks/PERK_UUID
```

This removes only the tier assignment. The reusable perk remains in the catalogue.

### Get perks available to a user

```bash
curl http://localhost:8080/api/v1/users/USER_UUID/perks
```

The API checks the user's active subscription, identifies its tier, and returns only active
perks assigned to that tier. Unassigned perks and inactive perks are excluded. A user without
an active subscription receives HTTP `404 Not Found`.

## Configurable order discounts

Order discounts are ordinary `PERCENTAGE_DISCOUNT` perks. Create them through the perk catalogue
and assign them to membership tiers through the tier-perk API.

Each discount perk uses this configuration:

```json
{
  "discountPercent": 15.00,
  "maximumDiscount": 500.00
}
```

`maximumDiscount` is optional and is configured on the reusable perk.
When the current subscription provides multiple active percentage-discount perks, checkout
calculates every candidate and applies only the one producing the largest monetary discount.
Discount perks never stack.

### Evaluate an order discount

```bash
curl -X POST \
  http://localhost:8080/api/v1/users/USER_UUID/discount/evaluate \
  -H "Content-Type: application/json" \
  -d '{"orderAmount": 1600.00}'
```

Example result:

```json
{
  "orderAmount": 1600.00,
  "discountPercent": 20.00,
  "discountAmount": 320.00,
  "finalAmount": 1280.00,
  "applied": true
}
```

A user without an active subscription receives no membership discount because active
subscription lookup fails with HTTP `404 Not Found`. Inactive, malformed, non-positive, and
non-percentage perks are ignored.

## Minimal order and checkout APIs

Order creation persists the order, snapshots current membership benefits, and triggers the
post-order tier reevaluation hook.

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

Example subscribed-user response:

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
  "membershipTierCode": "GOLD"
}
```

The optional category is persisted for future category-specific discount rules. A user without
an active subscription can still place an order, but receives zero discount, no free delivery,
and a `null` membership tier.

### Get one order

```bash
curl http://localhost:8080/api/v1/orders/ORDER_UUID
```

### Get a user's order history

```bash
curl http://localhost:8080/api/v1/orders/users/USER_UUID
```

The stored discount, delivery flag, and membership tier are snapshots and do not change when an
administrator later edits perks.

## Mock payment APIs

The payment module stores tokenized payment method metadata and mock payment transactions.
Never send real card numbers, CVVs, UPI PINs, or bank credentials.

### Add a payment method

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

The provider token is stored for mock processing but is never returned in API responses. The
first active method automatically becomes the default.

### List active payment methods

```bash
curl http://localhost:8080/api/v1/users/USER_UUID/payment-methods
```

### Remove a payment method

```bash
curl -i -X DELETE \
  http://localhost:8080/api/v1/users/USER_UUID/payment-methods/METHOD_UUID
```

Removal deactivates the method and preserves transaction history.

### Create a mock charge

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

Tokens beginning with `fail_` simulate a declined payment. Other tokens simulate success. Both
successful and failed attempts are persisted.

### Get payment history

```bash
curl http://localhost:8080/api/v1/users/USER_UUID/payments
```

## User homepage

After creating a user, subscription, and optional perk assignments, open:

```text
http://localhost:8080/home
```

The server-rendered HTML homepage displays:

- a personalized “Welcome to FirstClub” heading
- the current tier, billing cycle, and expiry date
- a cancellation notice when cancellation is scheduled
- active perks assigned to the subscription tier
- an upgrade button for Silver and Gold members
- a highest-tier message for Platinum members

The upgrade button currently opens the tier catalogue API; the paid upgrade REST API is ready
to be connected to a dedicated upgrade screen.
