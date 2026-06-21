-- Creates the core PostgreSQL schema for users, tiers, subscriptions, concurrency constraints,
-- expiry queries, and the initial Silver, Gold, and Platinum catalogue.

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    cohort VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tiers (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    tier_rank INTEGER NOT NULL UNIQUE CHECK (tier_rank > 0),
    monthly_price NUMERIC(12, 2) NOT NULL CHECK (monthly_price >= 0),
    quarterly_price NUMERIC(12, 2) NOT NULL CHECK (quarterly_price >= 0),
    yearly_price NUMERIC(12, 2) NOT NULL CHECK (yearly_price >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    tier_id UUID NOT NULL REFERENCES tiers(id),
    billing_cycle VARCHAR(20) NOT NULL
        CHECK (billing_cycle IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    starts_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at TIMESTAMPTZ,
    price_paid NUMERIC(12, 2) NOT NULL CHECK (price_paid >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (expires_at > starts_at)
);

CREATE UNIQUE INDEX uq_subscriptions_one_active_per_user
    ON subscriptions(user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_subscriptions_expiry
    ON subscriptions(status, expires_at);

INSERT INTO tiers (
    id, code, name, description, tier_rank,
    monthly_price, quarterly_price, yearly_price,
    active, version, created_at, updated_at
) VALUES
    ('1', 'SILVER', 'Silver',
     'Entry membership tier', 1, 199.00, 549.00, 1999.00, TRUE, 0, NOW(), NOW()),
    ('2', 'GOLD', 'Gold',
     'Enhanced membership tier', 2, 299.00, 799.00, 2999.00, TRUE, 0, NOW(), NOW()),
    ('3', 'PLATINUM', 'Platinum',
     'Highest membership tier', 3, 499.00, 1299.00, 4999.00, TRUE, 0, NOW(), NOW());
