# FirstClub Membership API Design

## 1. Recommended MVP scope

Build the backend first as a modular monolith:

- Authentication and role-based authorization (`USER`, `ADMIN`)
- Configurable membership plans: Monthly, Quarterly, Yearly
- Configurable tiers: Silver, Gold, Platinum
- Configurable perks assigned to tiers
- Subscribe, upgrade, downgrade, cancel, and view membership
- Subscription and tier-change history
- Automatic tier evaluation using order count, monthly spend, or cohort
- Checkout benefit evaluation so another shopping service can ask what benefits apply
- Admin audit fields and optimistic locking for concurrent updates

Payment processing can initially be represented by a mock payment provider. Keep a provider
interface so Stripe, Razorpay, or another gateway can be added later.

## 2. Domain model

### User

- `id: UUID`
- `email: String` (unique)
- `passwordHash: String`
- `firstName: String`
- `lastName: String`
- `role: USER | ADMIN`
- `cohort: String?`
- `enabled: boolean`
- `createdAt`, `updatedAt`

### MembershipPlan

Represents duration and price.

- `id: UUID`
- `code: MONTHLY | QUARTERLY | YEARLY`
- `name: String`
- `durationMonths: int`
- `price: BigDecimal`
- `currency: String`
- `active: boolean`
- `version: long`

### MembershipTier

- `id: UUID`
- `code: SILVER | GOLD | PLATINUM`
- `name: String`
- `rank: int` (used to determine upgrade/downgrade)
- `description: String`
- `active: boolean`
- `version: long`

### Perk

- `id: UUID`
- `code: String` (unique)
- `name: String`
- `type: FREE_DELIVERY | PERCENTAGE_DISCOUNT | EARLY_ACCESS | PRIORITY_SUPPORT | EXCLUSIVE_COUPON | CUSTOM`
- `description: String`
- `configuration: JSON`
- `active: boolean`
- `version: long`

Examples of `configuration`:

```json
{ "discountPercent": 10, "categoryIds": ["grocery"], "maxDiscount": 500 }
```

```json
{ "deliveryFee": 0, "eligibleOrderMinimum": 499 }
```

### TierPerk

- `id: UUID`
- `tierId: UUID`
- `perkId: UUID`
- `configurationOverride: JSON?`
- `validFrom`, `validUntil`
- unique constraint on active `tierId + perkId`

### Subscription

- `id: UUID`
- `userId: UUID`
- `planId: UUID`
- `tierId: UUID`
- `status: PENDING | ACTIVE | CANCELLED | EXPIRED | PAYMENT_FAILED`
- `startsAt: Instant`
- `expiresAt: Instant`
- `cancelledAt: Instant?`
- `cancelAtPeriodEnd: boolean`
- `autoRenew: boolean`
- `pricePaid: BigDecimal`
- `currency: String`
- `paymentReference: String?`
- `version: long`
- `createdAt`, `updatedAt`

Only one non-expired active subscription is allowed per user.

### SubscriptionChange

- `id: UUID`
- `subscriptionId: UUID`
- `type: CREATED | UPGRADED | DOWNGRADED | CANCELLED | RENEWED | EXPIRED | TIER_AUTO_CHANGED`
- `oldPlanId`, `newPlanId`
- `oldTierId`, `newTierId`
- `effectiveAt: Instant`
- `reason: String?`
- `createdBy: UUID?`
- `createdAt`

### TierRule

- `id: UUID`
- `name: String`
- `targetTierId: UUID`
- `ruleType: ORDER_COUNT | MONTHLY_SPEND | COHORT`
- `operator: GT | GTE | EQ | IN`
- `thresholdValue: String`
- `priority: int`
- `active: boolean`
- `version: long`

### UserMembershipMetric

- `userId: UUID`
- `period: YearMonth`
- `orderCount: long`
- `totalOrderValue: BigDecimal`
- `currency: String`
- `version: long`

### RefreshToken

- `id: UUID`
- `userId: UUID`
- `tokenHash: String`
- `expiresAt: Instant`
- `revokedAt: Instant?`

## 3. API conventions

- Base path: `/api/v1`
- JSON request and response bodies
- JWT access token in `Authorization: Bearer <token>`
- Short-lived access token plus rotating refresh token
- UUID identifiers
- UTC timestamps in ISO-8601
- Pagination: `?page=0&size=20&sort=createdAt,desc`
- Mutation endpoints accept `Idempotency-Key` where duplicate payment/subscription actions are risky
- Use `ProblemDetail` (`application/problem+json`) for errors
- Never accept price or expiry calculated by the frontend; calculate them on the server

## 4. Authentication APIs

### `POST /api/v1/auth/signup`

Public. Creates a user.

Request:

```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!",
  "firstName": "Alex",
  "lastName": "Roy"
}
```

Response: `201 Created` with user summary. Email verification can be added later.

### `POST /api/v1/auth/login`

Public.

Request:

```json
{ "email": "user@example.com", "password": "StrongPassword123!" }
```

Response:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### `POST /api/v1/auth/refresh`

Public endpoint using a valid refresh token. Rotates the refresh token.

### `POST /api/v1/auth/logout`

Authenticated. Revokes the supplied refresh token.

### `GET /api/v1/users/me`

Authenticated. Returns profile and role.

### `PATCH /api/v1/users/me`

Authenticated. Updates allowed profile fields.

## 5. Public catalogue APIs

### `GET /api/v1/membership-options`

Returns active plan/tier combinations with prices, durations, and perks. This is the main
endpoint for the subscription-selection screen.

Optional filters: `planCode`, `tierCode`, `currency`.

### `GET /api/v1/plans`

Returns active Monthly, Quarterly, and Yearly plans.

### `GET /api/v1/plans/{planId}`

Returns one active plan.

### `GET /api/v1/tiers`

Returns active tiers ordered by rank, including their perks.

### `GET /api/v1/tiers/{tierId}`

Returns one tier and its perks.

## 6. User subscription APIs

### `POST /api/v1/subscriptions/preview`

Authenticated. Calculates the final price, duration, start date, expiry date, and change
effect before payment.

Request:

```json
{
  "planId": "uuid",
  "tierId": "uuid",
  "couponCode": "OPTIONAL"
}
```

### `POST /api/v1/subscriptions`

Authenticated. Creates a subscription. Requires `Idempotency-Key`.

Request:

```json
{
  "planId": "uuid",
  "tierId": "uuid",
  "paymentMethodToken": "mock-token",
  "autoRenew": true
}
```

Response: `201 Created` with the active subscription and receipt summary.

### `GET /api/v1/subscriptions/current`

Authenticated. Returns the current membership, plan, tier, expiry, renewal status, and perks.
If none exists, return `404` with code `NO_ACTIVE_SUBSCRIPTION`.

### `GET /api/v1/subscriptions/history`

Authenticated and paginated. Returns past subscriptions and changes.

### `POST /api/v1/subscriptions/current/change-preview`

Authenticated. Shows price/effective-date consequences before an upgrade or downgrade.

Request:

```json
{
  "newPlanId": "uuid",
  "newTierId": "uuid"
}
```

### `POST /api/v1/subscriptions/current/change`

Authenticated. Requires `Idempotency-Key`.

Suggested policy:

- Upgrade: immediate; charge the configured difference/proration.
- Downgrade: effective at the next renewal date.
- Plan duration change: effective at the next renewal date unless explicitly configured.

Request:

```json
{
  "newPlanId": "uuid",
  "newTierId": "uuid",
  "paymentMethodToken": "mock-token"
}
```

### `POST /api/v1/subscriptions/current/cancel`

Authenticated.

Request:

```json
{ "reason": "Not using it enough" }
```

Cancellation is scheduled for period end, benefits remain active until `expiresAt`, and no
refund is issued. Calling it repeatedly should be idempotent.

### `POST /api/v1/subscriptions/current/reactivate`

Authenticated. Reverses a scheduled cancellation before the subscription expires.

### `PATCH /api/v1/subscriptions/current/renewal`

Authenticated.

Request:

```json
{ "autoRenew": false }
```

## 7. Membership and checkout integration APIs

### `GET /api/v1/memberships/me`

Returns a home-screen-friendly aggregate:

- welcome/profile information
- active plan and tier
- subscription status and expiry
- current perks
- upgrade availability
- scheduled subscription changes

### `POST /api/v1/benefits/evaluate`

Authenticated or service-to-service. Evaluates benefits for a cart without changing data.

Request:

```json
{
  "cartId": "cart-123",
  "items": [
    {
      "productId": "p1",
      "categoryId": "grocery",
      "quantity": 2,
      "unitPrice": 250
    }
  ],
  "deliveryFee": 40,
  "currency": "INR"
}
```

Response:

```json
{
  "tierCode": "GOLD",
  "discount": 50,
  "deliveryDiscount": 40,
  "finalAmount": 450,
  "appliedPerks": ["GOLD_CATEGORY_DISCOUNT", "FREE_DELIVERY"]
}
```

### `POST /api/v1/internal/orders`

Service-to-service endpoint/event adapter. Records a completed order for tier evaluation.
Requires an order ID as an idempotency key.

Request:

```json
{
  "orderId": "order-123",
  "userId": "uuid",
  "total": 2500,
  "currency": "INR",
  "completedAt": "2026-06-21T12:00:00Z"
}
```

### `POST /api/v1/internal/users/{userId}/tier-evaluation`

Admin/service endpoint to manually trigger rule evaluation. A scheduled job can run the same
service for all eligible users.

## 8. Admin APIs

All admin APIs require `ROLE_ADMIN`.

### Plans

- `POST /api/v1/admin/plans`
- `GET /api/v1/admin/plans`
- `GET /api/v1/admin/plans/{id}`
- `PUT /api/v1/admin/plans/{id}`
- `PATCH /api/v1/admin/plans/{id}/status`

Do not hard-delete plans referenced by subscriptions; deactivate them.

### Tiers

- `POST /api/v1/admin/tiers`
- `GET /api/v1/admin/tiers`
- `GET /api/v1/admin/tiers/{id}`
- `PUT /api/v1/admin/tiers/{id}`
- `PATCH /api/v1/admin/tiers/{id}/status`

### Perks and tier assignments

- `POST /api/v1/admin/perks`
- `GET /api/v1/admin/perks`
- `GET /api/v1/admin/perks/{id}`
- `PUT /api/v1/admin/perks/{id}`
- `PATCH /api/v1/admin/perks/{id}/status`
- `PUT /api/v1/admin/tiers/{tierId}/perks/{perkId}`
- `DELETE /api/v1/admin/tiers/{tierId}/perks/{perkId}`

The assignment endpoint accepts validity dates and tier-specific configuration overrides.

### Tier rules

- `POST /api/v1/admin/tier-rules`
- `GET /api/v1/admin/tier-rules`
- `GET /api/v1/admin/tier-rules/{id}`
- `PUT /api/v1/admin/tier-rules/{id}`
- `PATCH /api/v1/admin/tier-rules/{id}/status`
- `DELETE /api/v1/admin/tier-rules/{id}` (only when unused; otherwise deactivate)

### Subscription operations and reporting

- `GET /api/v1/admin/subscriptions`
- `GET /api/v1/admin/subscriptions/{id}`
- `GET /api/v1/admin/users/{userId}/membership`
- `POST /api/v1/admin/users/{userId}/tier-evaluation`
- `POST /api/v1/admin/subscriptions/{id}/expire` (support/testing operation)

## 9. Standard errors

Important error codes:

- `VALIDATION_FAILED` — `400`
- `INVALID_CREDENTIALS` — `401`
- `TOKEN_EXPIRED` — `401`
- `ACCESS_DENIED` — `403`
- `RESOURCE_NOT_FOUND` — `404`
- `NO_ACTIVE_SUBSCRIPTION` — `404`
- `EMAIL_ALREADY_EXISTS` — `409`
- `ACTIVE_SUBSCRIPTION_EXISTS` — `409`
- `STALE_SUBSCRIPTION_VERSION` — `409`
- `INVALID_TIER_CHANGE` — `422`
- `PAYMENT_FAILED` — `422`
- `IDEMPOTENCY_KEY_REUSED` — `422`

## 10. Spring Boot dependencies

Select these from Spring Initializr:

### Required

- Spring Web
- Spring Security
- OAuth2 Resource Server (JWT validation and JWT decoder support)
- Spring Data JPA
- Validation
- PostgreSQL Driver
- Flyway Migration
- Spring Boot Actuator

### API documentation

- `org.springdoc:springdoc-openapi-starter-webmvc-ui`

### Recommended implementation helpers

- Lombok (optional; records can reduce the need for it)
- MapStruct (optional DTO/entity mapping)

### Testing

- Spring Boot Starter Test
- Spring Security Test
- Testcontainers JUnit Jupiter
- Testcontainers PostgreSQL

### Optional later

- Spring Data Redis for distributed idempotency, caching, or refresh-token storage
- Spring Retry for transient payment-provider failures
- Micrometer Prometheus registry
- Mail sender for verification and expiry reminders
- Messaging (Kafka or RabbitMQ) if order events arrive asynchronously

JWT can be implemented with Spring Security's `JwtEncoder` and `JwtDecoder`; a separate JJWT
library is not required. Passwords should use `BCryptPasswordEncoder` or
`DelegatingPasswordEncoder`.

## 11. Suggested package structure

```text
com.firstclub.membership
├── auth
├── user
├── plan
├── tier
├── perk
├── subscription
├── benefit
├── order
├── payment
├── admin
└── common
    ├── config
    ├── error
    ├── security
    └── audit
```

Each feature package should contain its controller, service, repository, entities, and DTOs.
Avoid one global `controller/service/repository` package split.

## 12. Important implementation decisions

- Use PostgreSQL `jsonb` for flexible perk configuration.
- Use `BigDecimal`, never `double`, for money.
- Store an immutable snapshot of price and important perk terms on the subscription/change
  record so later admin edits do not rewrite history.
- Add `@Version` to plans, tiers, perks, subscriptions, and metrics.
- Lock the user's current subscription row during subscribe/change/cancel operations.
- Add a database constraint/index to prevent multiple active subscriptions for one user.
- Make order ingestion and payment/subscription creation idempotent.
- Run expiry/renewal processing with a scheduled job; design the service so it can later move
  to a queue worker.
- Audit every admin configuration change.
- Keep controllers thin; put lifecycle rules in a transactional domain service.
- Seed an admin user and sample plans/tiers/perks only in the `dev` profile.

## 13. Suggested delivery order

1. Project setup, database migrations, error model, and OpenAPI.
2. Signup/login/refresh/logout and role security.
3. Plan, tier, and perk admin CRUD.
4. Public membership catalogue.
5. Subscribe and current-membership APIs with mock payment.
6. Upgrade, downgrade, cancel, renewal, and history.
7. Tier rules, order ingestion, and automatic evaluation.
8. Benefit evaluation for checkout.
9. Actuator, tests, Docker Compose, and basic frontend.
