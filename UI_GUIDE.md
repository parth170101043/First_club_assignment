# FirstClub UI Guide

This guide explains how to use the browser interface as a member or administrator.

## Start the application

From the project directory:

```bash
./setup-and-run.sh
```

The script installs missing local prerequisites where supported, starts Docker Desktop or
Docker Engine, starts PostgreSQL, applies Flyway migrations, creates the initial administrator,
and runs FirstClub on [http://localhost:8080](http://localhost:8080).

The local administrator credentials are stored in `.firstclub.env`. Change them before the
first run if you do not want to use the generated development defaults.

Press `Ctrl+C` to stop the application. PostgreSQL continues running in Docker and will be
reused on the next run.

## Member guide

### Create an account

1. Open [http://localhost:8080/signup](http://localhost:8080/signup).
2. Enter a unique email, name, password, and optional cohort.
3. Submit the form and sign in.

Email addresses are case-insensitively unique. Cohorts such as `EARLY_ADOPTER` or `VIP` can
qualify the member for a behavioral tier when they match the current admin configuration.

### Shop and place an order

1. Sign in with a member account. The app opens the Shop.
2. Choose quantities and select **Add to cart**.
3. Use **Update** in the cart to change a quantity. Set it to zero to remove the item.
4. Select **Place order**.

The confirmation page shows:

- purchased products, quantities, and line totals;
- item subtotal;
- the single discount perk that saved the most money;
- free delivery when the membership includes it, otherwise a ₹50 delivery fee;
- final payable price.

The cart is cleared after a successful order.

### Purchase a membership

1. Open **Membership options**.
2. Choose Silver, Gold, or Platinum and a billing cycle.
3. Enter display-only mock card details.
4. Select **Pay securely and activate**.

No real card is charged. The selected mock payment method is retained for automatic renewal.

### Upgrade a membership

From the membership home, select **Upgrade membership**.

- Only tiers above the currently paid tier are displayed.
- The billing cycle and current expiry date stay unchanged.
- Only the prorated price difference from the upgrade time to the existing expiry is charged.
- Higher-tier perks become available immediately.

### Downgrade or cancel a membership

The membership card always shows what will happen at the current expiry date:

- no pending change: **Subscription auto-renews on date**;
- scheduled downgrade: **Subscription auto-renews to [tier] on date**;
- scheduled cancellation: **Subscription expires on date**.

Use the downgrade selector to choose a lower paid tier. Current benefits remain unchanged until
renewal. Use **Cancel membership** to stop renewal while keeping benefits through the current
expiry. A scheduled cancellation can be reversed with **Keep membership and auto-renew**.

### Automatic and behavioral tiers

The effective tier is the higher of:

- the tier the member paid for; and
- the tier earned from behavioral rules.

Behavioral rules can use current-month order count, current-month original order value, and
the user cohort. They do not charge the member or change subscription expiry. After each
order, the app evaluates every rule and applies the highest earned tier.

### Automatic renewal

At expiry, the app charges the stored mock payment method using the current configured price.
Successful payment starts the next period from the previous expiry. A scheduled cancellation
or failed payment ends the subscription instead.

## Administrator guide

### Sign in

Open [http://localhost:8080/login](http://localhost:8080/login) and use the administrator
credentials from `.firstclub.env`. Administrators are redirected to `/admin`.

### Product catalogue

In **Products**:

1. Enter a unique SKU and product name.
2. Enter price, optional category, and description.
3. Select **Add product**.

Removing a product hides it from new orders. Historical order details remain unchanged.
Inventory and stock levels are not tracked.

### Perk catalogue

In **Perks**:

1. Enter a unique code and name.
2. Select a perk type.
3. For percentage discounts, enter the percentage and optional maximum discount.
4. Select **Create perk**.

A perk cannot be deleted while assigned to a tier. Remove all tier assignments first.

`FREE_DELIVERY` removes the standard ₹50 delivery fee. Percentage discount perks do not
stack—the application applies only the perk producing the largest monetary saving.

### Assign perks to tiers

In **Tier perks**:

1. Choose a membership tier.
2. Choose a perk.
3. Select **Assign perk**.

Assignments use the configuration stored on the perk. The same perk cannot be assigned to the
same tier twice.

### Configure behavioral tier rules

In **Behavioral rules**, configure Gold and Platinum values for:

- monthly order count;
- monthly original order value;
- cohort name.

Platinum thresholds must be greater than or equal to Gold thresholds, and the cohort names
must differ. Saving immediately reevaluates all active subscriptions. The highest matching
rule wins, while an earned tier can never reduce a member below their paid tier.

### Manage subscriptions

In **Subscriptions**:

- create an admin-issued subscription for a member without charging a payment method;
- review plan, current effective tier, status, and expiry;
- end an active subscription immediately.

Member-purchased subscriptions use mock payments and support prorated upgrades and automatic
renewal.

## Common problems

### Docker daemon is not running

On macOS, open Docker Desktop and wait for it to finish starting. Then rerun:

```bash
./setup-and-run.sh
```

### Port 8080 is already in use

The setup script now detects when FirstClub is already healthy and exits successfully without
starting a duplicate process. Do not run both `./setup-and-run.sh` and
`./mvnw spring-boot:run` at the same time.

If another application owns port 8080, stop it or identify it with:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

### Reset the local database

This permanently deletes local FirstClub data:

```bash
docker compose down -v
```

Run `./setup-and-run.sh` afterward to create a fresh database.
