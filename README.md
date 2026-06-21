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


## Authentication

Open `/signup` to create a member account and `/login` to sign in. Successful login creates an
HTTP-only `JSESSIONID` cookie and redirects to `/home`. Browser forms use CSRF protection.

To bootstrap the first administrator, set:

```bash
export FIRSTCLUB_ADMIN_EMAIL=admin@example.com
export FIRSTCLUB_ADMIN_PASSWORD='replace-with-a-strong-password'
```

Admin APIs require `ROLE_ADMIN`; member APIs require authentication and enforce account
ownership for user, subscription, payment, discount, perk, and order resources. After an
administrator signs in, the browser redirects to `http://localhost:8080/admin`.

The admin console supports product catalogue management, perk creation/deletion, tier-perk
assignment and removal, behavioral rule configuration, and manual subscription creation/ending.
Members browse active products at `/shop`, keep quantities in a session cart, and place one
multi-item order; inventory and stock availability are intentionally not tracked. See
[UI_GUIDE.md](UI_GUIDE.md) for the full browser walkthrough.

## REST API summary

Full request/response examples, validation rules, and error responses for every endpoint are in
[userdoc.md](userdoc.md). This table is a quick index; see [solution.md](solution.md) for the
domain model and design rationale behind the tier and subscription lifecycle.

| Area | Endpoints | Notes |
|---|---|---|
| Users | `POST /api/v1/users`, `GET /api/v1/users`, `GET /api/v1/users/{id}`, `DELETE /api/v1/users/{id}` | Deletion blocked (`409`) if the user has subscription history |
| Tiers | `GET /api/v1/tiers`, `PATCH /api/v1/admin/tiers/{id}/subscription-prices` | Silver/Gold/Platinum seeded by Flyway; price changes are admin-only |
| Plans | `GET /api/v1/plans`, `GET /api/v1/membership-options` | Billing cycles × tier price combinations |
| Subscriptions | `POST /api/v1/subscriptions`, `.../upgrade`, `.../downgrade`, `.../current`, `.../history`, `.../cancel`, `.../reactivate`, `/api/v1/subscriptions/expire-due` | `minTier` (paid) vs `computedBehavioralTier` (earned) vs `currentTier = max(both)`; downgrade and cancellation take effect at renewal, no refunds; subscription writes use pessimistic locking plus JPA `@Version` and return `409 Conflict` on stale writes |
| Perks | `POST/GET/PUT/DELETE /api/v1/admin/perks`, tier-assignment endpoints, `GET /api/v1/users/{id}/perks` | Catalogue and tier assignment are separate; perks can't be deleted while assigned to a tier |
| Order discounts | `POST /api/v1/users/{id}/discount/evaluate` | Highest-value percentage discount perk wins; perks never stack |
| Orders | `POST /api/v1/orders`, `GET /api/v1/orders/{id}`, `GET /api/v1/orders/users/{id}` | Snapshots discount/delivery/tier at order time; triggers behavioral tier reevaluation |
| Payments (mock) | `POST/GET/DELETE /api/v1/users/{id}/payment-methods`, `POST /api/v1/payments/charge`, `GET /api/v1/users/{id}/payments` | No real card data stored; tokens starting `fail_` simulate a decline |

## User homepage

After creating a user, subscription, and optional perk assignments, open
`http://localhost:8080/home`. The server-rendered homepage displays the current tier, billing
cycle, expiry date, any pending cancellation, active perks, and an upgrade button for Silver and
Gold members.
